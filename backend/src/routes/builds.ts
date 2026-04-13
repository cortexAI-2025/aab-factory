import { FastifyInstance } from 'fastify';
import { verifyFirebaseToken } from '../middleware/auth';
import { requireBuildAccess } from '../middleware/subscription';
import { queueBuild, getBuildStatus, listBuilds } from '../services/buildService';
import { getFirestore } from '../config/firebase';
import { z } from 'zod';

const BuildTriggerSchema = z.object({
  projectId: z.string().min(1),
  keystoreType: z.enum(['system', 'user']).default('system'),
  userKeystoreUrl: z.string().url().optional(),
});

export async function buildRoutes(fastify: FastifyInstance): Promise<void> {
  /**
   * POST /builds/trigger
   * Queues an AAB build for a project.
   * Checks subscription limits before proceeding.
   */
  fastify.post('/trigger', {
    preHandler: [verifyFirebaseToken, requireBuildAccess],
  }, async (request, reply) => {
    const body = BuildTriggerSchema.safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({ error: 'ValidationError', message: body.error.message });
    }

    // Verify the project belongs to this user
    const db = getFirestore();
    const projectDoc = await db
      .collection('users').doc(request.uid)
      .collection('projects').doc(body.data.projectId).get();

    if (!projectDoc.exists) {
      return reply.status(404).send({ error: 'NotFound', message: 'Project not found' });
    }

    try {
      const buildResponse = await queueBuild({
        projectId: body.data.projectId,
        userId: request.uid,
        projectData: projectDoc.data()!,
        keystoreType: body.data.keystoreType,
        userKeystoreUrl: body.data.userKeystoreUrl,
      });

      return reply.status(202).send(buildResponse);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Build trigger failed';
      fastify.log.error(err, 'Build trigger error');
      return reply.status(500).send({ error: 'BuildError', message });
    }
  });

  /**
   * GET /builds/:buildId/status
   * Returns the current status of a build.
   */
  fastify.get<{ Params: { buildId: string } }>('/:buildId/status', {
    preHandler: [verifyFirebaseToken],
  }, async (request, reply) => {
    try {
      const status = await getBuildStatus(request.params.buildId, request.uid);
      if (!status) {
        return reply.status(404).send({ error: 'NotFound', message: 'Build not found' });
      }
      return reply.send(status);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Status fetch failed';
      return reply.status(500).send({ error: 'BuildError', message });
    }
  });

  /**
   * GET /builds
   * Lists all builds for the current user.
   */
  fastify.get('/', {
    preHandler: [verifyFirebaseToken],
  }, async (request, reply) => {
    const builds = await listBuilds(request.uid);
    return reply.send({ builds });
  });
}
