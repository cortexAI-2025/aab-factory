import Stripe from 'stripe';
import { getFirestore } from '../config/firebase';

let stripeClient: Stripe | null = null;

function getStripe(): Stripe {
  if (!stripeClient) {
    stripeClient = new Stripe(process.env.STRIPE_SECRET_KEY!, {
      apiVersion: '2024-04-10',
    });
  }
  return stripeClient;
}

const PRICE_IDS: Record<string, string> = {
  pro_monthly: process.env.STRIPE_PRO_PRICE_ID ?? '',
  build_pack_5: process.env.STRIPE_BUILD_PACK_PRICE_ID ?? '',
};

interface CheckoutParams {
  uid: string;
  email: string;
  planId: string;
  successUrl: string;
  cancelUrl: string;
}

/**
 * Creates or retrieves a Stripe customer for the user, then creates a checkout session.
 */
export async function createCheckoutSession(params: CheckoutParams): Promise<Stripe.Checkout.Session> {
  const stripe = getStripe();
  const db = getFirestore();

  // Get or create Stripe customer
  const userDoc = await db.collection('users').doc(params.uid).get();
  let customerId: string = userDoc.data()?.stripeCustomerId ?? '';

  if (!customerId) {
    const customer = await stripe.customers.create({
      email: params.email,
      metadata: { firebaseUid: params.uid },
    });
    customerId = customer.id;
    await db.collection('users').doc(params.uid).update({
      stripeCustomerId: customerId,
      updatedAt: new Date().toISOString(),
    });
  }

  const priceId = PRICE_IDS[params.planId];
  if (!priceId) throw new Error(`Unknown plan: ${params.planId}`);

  const isSubscription = params.planId === 'pro_monthly';

  const session = await stripe.checkout.sessions.create({
    customer: customerId,
    payment_method_types: ['card'],
    line_items: [{ price: priceId, quantity: 1 }],
    mode: isSubscription ? 'subscription' : 'payment',
    success_url: params.successUrl,
    cancel_url: params.cancelUrl,
    metadata: {
      firebaseUid: params.uid,
      planId: params.planId,
    },
    subscription_data: isSubscription
      ? { metadata: { firebaseUid: params.uid } }
      : undefined,
  });

  return session;
}

/**
 * Handles incoming Stripe webhook events.
 * Verifies the signature and updates user plan in Firestore.
 */
export async function handleWebhookEvent(payload: Buffer, signature: string): Promise<void> {
  const stripe = getStripe();
  const webhookSecret = process.env.STRIPE_WEBHOOK_SECRET!;

  let event: Stripe.Event;
  try {
    event = stripe.webhooks.constructEvent(payload, signature, webhookSecret);
  } catch (err) {
    throw new Error(`Webhook signature verification failed: ${(err as Error).message}`);
  }

  const db = getFirestore();

  switch (event.type) {
    case 'checkout.session.completed': {
      const session = event.data.object as Stripe.Checkout.Session;
      const uid = session.metadata?.firebaseUid;
      const planId = session.metadata?.planId;
      if (!uid) break;

      if (planId === 'pro_monthly') {
        await db.collection('users').doc(uid).update({
          plan: 'pro',
          stripeSubscriptionId: session.subscription ?? '',
          subscriptionStatus: 'active',
          hasWatermark: false,
          updatedAt: new Date().toISOString(),
        });
        // Remove watermark from all projects
        await removeWatermarkFromProjects(uid);
      } else if (planId === 'build_pack_5') {
        await db.collection('users').doc(uid).update({
          buildsUsedThisMonth: 0, // Reset counter as a pack purchase bonus
          updatedAt: new Date().toISOString(),
        });
      }
      break;
    }

    case 'invoice.paid': {
      const invoice = event.data.object as Stripe.Invoice;
      const customerId = invoice.customer as string;
      await updateUserByCustomerId(db, customerId, {
        subscriptionStatus: 'active',
        updatedAt: new Date().toISOString(),
      });
      break;
    }

    case 'invoice.payment_failed': {
      const invoice = event.data.object as Stripe.Invoice;
      const customerId = invoice.customer as string;
      await updateUserByCustomerId(db, customerId, {
        subscriptionStatus: 'past_due',
        updatedAt: new Date().toISOString(),
      });
      break;
    }

    case 'customer.subscription.deleted': {
      const subscription = event.data.object as Stripe.Subscription;
      const customerId = subscription.customer as string;
      await updateUserByCustomerId(db, customerId, {
        plan: 'free',
        subscriptionStatus: 'canceled',
        stripeSubscriptionId: '',
        hasWatermark: true,
        updatedAt: new Date().toISOString(),
      });
      break;
    }

    case 'customer.subscription.updated': {
      const subscription = event.data.object as Stripe.Subscription;
      const customerId = subscription.customer as string;
      await updateUserByCustomerId(db, customerId, {
        subscriptionStatus: subscription.status,
        billingPeriodEnd: new Date(subscription.current_period_end * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
      });
      break;
    }

    default:
      // Unhandled event type — log and ignore
      console.log(`Unhandled Stripe event: ${event.type}`);
  }
}

/**
 * Returns subscription status for a user.
 */
export async function getCustomerSubscription(uid: string): Promise<{
  plan: string;
  status: string;
  buildsUsed: number;
  billingPeriodEnd: string | null;
}> {
  const db = getFirestore();
  const userDoc = await db.collection('users').doc(uid).get();
  const data = userDoc.data() ?? {};

  return {
    plan: data.plan ?? 'free',
    status: data.subscriptionStatus ?? 'none',
    buildsUsed: data.buildsUsedThisMonth ?? 0,
    billingPeriodEnd: data.billingPeriodEnd ?? null,
  };
}

async function updateUserByCustomerId(
  db: FirebaseFirestore.Firestore,
  customerId: string,
  updates: Record<string, unknown>
): Promise<void> {
  const snapshot = await db.collection('users')
    .where('stripeCustomerId', '==', customerId)
    .limit(1)
    .get();

  if (!snapshot.empty) {
    await snapshot.docs[0].ref.update(updates);
  }
}

async function removeWatermarkFromProjects(uid: string): Promise<void> {
  const db = getFirestore();
  const projects = await db.collection('users').doc(uid).collection('projects').get();
  const batch = db.batch();
  projects.docs.forEach(doc => batch.update(doc.ref, { hasWatermark: false }));
  await batch.commit();
}

export async function createStripeCustomer(uid: string, email: string): Promise<string> {
  const stripe = getStripe();
  const customer = await stripe.customers.create({
    email,
    metadata: { firebaseUid: uid },
  });
  return customer.id;
}
