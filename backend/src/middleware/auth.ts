import { FastifyRequest, FastifyReply } from 'fastify';
import { getAuth } from '../config/firebase';

declare module 'fastify' {
  interface FastifyRequest {
    uid: string;
    userEmail: string;
  }
}

/**
 * Verifies the Firebase ID token from the Authorization header.
 * Attaches uid and userEmail to the request object.
 */
export async function verifyFirebaseToken(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  const authHeader = request.headers.authorization;

  if (!authHeader?.startsWith('Bearer ')) {
    reply.status(401).send({ error: 'Unauthorized', message: 'Missing Bearer token' });
    return;
  }

  const idToken = authHeader.slice(7);

  try {
    const decoded = await getAuth().verifyIdToken(idToken);
    request.uid = decoded.uid;
    request.userEmail = decoded.email ?? '';
  } catch (err) {
    reply.status(401).send({ error: 'Unauthorized', message: 'Invalid or expired token' });
  }
}
