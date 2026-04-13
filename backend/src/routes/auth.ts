import { FastifyInstance } from 'fastify';
import { verifyFirebaseToken } from '../middleware/auth';
import { getFirestore } from '../config/firebase';
import { z } from 'zod';

const RegisterSchema = z.object({
  displayName: z.string().min(1).max(60),
  fcmToken: z.string().optional(),
});

export async function authRoutes(fastify: FastifyInstance): Promise<void> {
  /**
   * POST /auth/register
   * Called after Firebase Auth sign-up to create the Firestore user document.
   */
  fastify.post('/register', {
    preHandler: verifyFirebaseToken,
  }, async (request, reply) => {
    const body = RegisterSchema.safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({ error: 'ValidationError', message: body.error.message });
    }

    const db = getFirestore();
    const userRef = db.collection('users').doc(request.uid);
    const existing = await userRef.get();

    if (!existing.exists) {
      await userRef.set({
        uid: request.uid,
        email: request.userEmail,
        displayName: body.data.displayName,
        plan: 'free',
        stripeCustomerId: '',
        stripeSubscriptionId: '',
        subscriptionStatus: 'none',
        buildsUsedThisMonth: 0,
        fcmToken: body.data.fcmToken ?? '',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });
    } else if (body.data.fcmToken) {
      // Update FCM token on re-login
      await userRef.update({ fcmToken: body.data.fcmToken, updatedAt: new Date().toISOString() });
    }

    const userData = (await userRef.get()).data();
    return reply.status(200).send({ user: userData });
  });

  /**
   * GET /auth/me
   * Returns the current user's profile and plan info.
   */
  fastify.get('/me', {
    preHandler: verifyFirebaseToken,
  }, async (request, reply) => {
    const db = getFirestore();
    const userDoc = await db.collection('users').doc(request.uid).get();

    if (!userDoc.exists) {
      return reply.status(404).send({ error: 'NotFound', message: 'User profile not found' });
    }

    return reply.send({ user: userDoc.data() });
  });

  /**
   * DELETE /auth/account
   * Deletes the user's Firestore data (Firebase Auth deletion is client-side).
   */
  fastify.delete('/account', {
    preHandler: verifyFirebaseToken,
  }, async (request, reply) => {
    const db = getFirestore();
    const batch = db.batch();

    // Delete all projects
    const projects = await db.collection('users').doc(request.uid)
      .collection('projects').listDocuments();
    projects.forEach(doc => batch.delete(doc));

    // Delete user document
    batch.delete(db.collection('users').doc(request.uid));
    await batch.commit();

    return reply.status(200).send({ message: 'Account data deleted' });
  });
}
