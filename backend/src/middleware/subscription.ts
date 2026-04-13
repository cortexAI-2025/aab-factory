import { FastifyRequest, FastifyReply } from 'fastify';
import admin from 'firebase-admin';
import { getFirestore } from '../config/firebase';

const FREE_BUILDS_PER_MONTH = 2;

/**
 * Checks whether the user's current plan allows a build action.
 * Free plan: max 2 builds/month
 * Pro plan: unlimited
 */
export async function requireBuildAccess(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  const db = getFirestore();
  const userDoc = await db.collection('users').doc(request.uid).get();

  if (!userDoc.exists) {
    // New user — default free plan, 0 builds used
    return;
  }

  const data = userDoc.data()!;
  const plan: string = data.plan ?? 'free';
  const buildsUsed: number = data.buildsUsedThisMonth ?? 0;

  if (plan === 'pro') return; // Unlimited for Pro

  if (buildsUsed >= FREE_BUILDS_PER_MONTH) {
    reply.status(402).send({
      error: 'PaymentRequired',
      message: `Free plan allows ${FREE_BUILDS_PER_MONTH} builds per month. Upgrade to Pro for unlimited builds.`,
      code: 'BUILD_LIMIT_REACHED',
    });
  }
}

/**
 * Checks whether the user can access a Pro-only template.
 */
export async function requireProForTemplate(
  request: FastifyRequest<{ Body: { templateId?: string } }>,
  reply: FastifyReply
): Promise<void> {
  const proTemplates = new Set(['ecommerce', 'lead-generation', 'local-services']);
  const templateId = request.body?.templateId;

  if (!templateId || !proTemplates.has(templateId)) return;

  const db = getFirestore();
  const userDoc = await db.collection('users').doc(request.uid).get();
  const plan = userDoc.data()?.plan ?? 'free';

  if (plan !== 'pro') {
    reply.status(403).send({
      error: 'Forbidden',
      message: 'This template requires a Pro subscription',
      code: 'PRO_REQUIRED',
    });
  }
}

/**
 * Increments the monthly build counter for a user.
 * Called by buildService after successfully queuing a build.
 */
export async function incrementBuildCounter(uid: string): Promise<void> {
  const db = getFirestore();
  await db.collection('users').doc(uid).update({
    buildsUsedThisMonth: admin.firestore.FieldValue.increment(1),
    updatedAt: new Date().toISOString(),
  });
}
