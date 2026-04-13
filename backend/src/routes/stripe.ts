import { FastifyInstance, FastifyRequest } from 'fastify';
import { verifyFirebaseToken } from '../middleware/auth';
import {
  createCheckoutSession,
  handleWebhookEvent,
  getCustomerSubscription,
  createStripeCustomer,
} from '../services/stripeService';
import { z } from 'zod';

const CheckoutSchema = z.object({
  planId: z.enum(['pro_monthly', 'build_pack_5']),
  successUrl: z.string().optional(),
  cancelUrl: z.string().optional(),
});

export async function stripeRoutes(fastify: FastifyInstance): Promise<void> {
  /**
   * POST /stripe/create-checkout-session
   * Creates a Stripe Checkout session for subscriptions or one-time payments.
   */
  fastify.post('/create-checkout-session', {
    preHandler: [verifyFirebaseToken],
  }, async (request, reply) => {
    const body = CheckoutSchema.safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({ error: 'ValidationError', message: body.error.message });
    }

    try {
      const session = await createCheckoutSession({
        uid: request.uid,
        email: request.userEmail,
        planId: body.data.planId,
        successUrl: body.data.successUrl ?? `${process.env.FRONTEND_URL}/success`,
        cancelUrl: body.data.cancelUrl ?? `${process.env.FRONTEND_URL}/cancel`,
      });

      return reply.send({
        sessionId: session.id,
        checkoutUrl: session.url,
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Checkout session creation failed';
      fastify.log.error(err, 'Stripe checkout error');
      return reply.status(500).send({ error: 'StripeError', message });
    }
  });

  /**
   * GET /stripe/subscription-status
   * Returns the current subscription status for the authenticated user.
   */
  fastify.get('/subscription-status', {
    preHandler: [verifyFirebaseToken],
  }, async (request, reply) => {
    try {
      const status = await getCustomerSubscription(request.uid);
      return reply.send(status);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Subscription fetch failed';
      return reply.status(500).send({ error: 'StripeError', message });
    }
  });

  /**
   * POST /stripe/webhook
   * Receives Stripe webhook events (signature verified).
   * This MUST use the raw body — do NOT use JSON parser for this route.
   */
  fastify.addContentTypeParser(
    'application/json',
    { parseAs: 'buffer', bodyLimit: 1_048_576 },
    (_req, body, done) => done(null, body)
  );

  fastify.post('/webhook', async (request: FastifyRequest, reply) => {
    const signature = request.headers['stripe-signature'];
    if (!signature || typeof signature !== 'string') {
      return reply.status(400).send({ error: 'Missing Stripe-Signature header' });
    }

    try {
      await handleWebhookEvent(request.body as Buffer, signature);
      return reply.status(200).send({ received: true });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Webhook processing failed';
      fastify.log.error(err, 'Stripe webhook error');
      return reply.status(400).send({ error: 'WebhookError', message });
    }
  });
}
