import { FastifyInstance } from 'fastify';
import { verifyFirebaseToken } from '../middleware/auth';
import { getFirestore } from '../config/firebase';
import { z } from 'zod';
import { v4 as uuidv4 } from 'uuid';

const AppConfigSchema = z.object({
  appName: z.string().min(1).max(60),
  packageName: z.string().regex(/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}$/),
  primaryColor: z.string().regex(/^#[0-9A-Fa-f]{6}$/),
  accentColor: z.string().regex(/^#[0-9A-Fa-f]{6}$/),
  backgroundColor: z.string().regex(/^#[0-9A-Fa-f]{6}$/).optional(),
  logoUrl: z.string().url().optional().or(z.literal('')),
  versionName: z.string().regex(/^\d+\.\d+\.\d+$/),
  versionCode: z.number().int().positive(),
  minSdkVersion: z.number().int().min(21),
  targetSdkVersion: z.number().int().min(33),
});

const ProjectCreateSchema = z.object({
  name: z.string().min(1).max(60),
  description: z.string().max(500).optional(),
  templateId: z.string().optional(),
  aiPrompt: z.string().max(2000).optional(),
  config: AppConfigSchema,
  sections: z.array(z.object({
    id: z.string(),
    type: z.string(),
    title: z.string().max(200),
    subtitle: z.string().max(500).optional(),
    content: z.string().max(2000).optional(),
    imageUrl: z.string().optional(),
    ctaText: z.string().max(50).optional(),
    ctaAction: z.string().max(200).optional(),
    isVisible: z.boolean(),
    order: z.number().int(),
  })),
});

export async function projectRoutes(fastify: FastifyInstance): Promise<void> {
  const preHandler = [verifyFirebaseToken];

  /**
   * GET /projects
   * List all projects for the authenticated user.
   */
  fastify.get('/', { preHandler }, async (request, reply) => {
    const db = getFirestore();
    const snapshot = await db
      .collection('users').doc(request.uid)
      .collection('projects')
      .orderBy('updatedAt', 'desc')
      .limit(50)
      .get();

    const projects = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    return reply.send({ projects });
  });

  /**
   * GET /projects/:id
   * Get a specific project.
   */
  fastify.get<{ Params: { id: string } }>('/:id', { preHandler }, async (request, reply) => {
    const db = getFirestore();
    const doc = await db.collection('users').doc(request.uid)
      .collection('projects').doc(request.params.id).get();

    if (!doc.exists) {
      return reply.status(404).send({ error: 'NotFound', message: 'Project not found' });
    }

    return reply.send({ project: { id: doc.id, ...doc.data() } });
  });

  /**
   * POST /projects
   * Create a new project.
   */
  fastify.post('/', { preHandler }, async (request, reply) => {
    const body = ProjectCreateSchema.safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({ error: 'ValidationError', message: body.error.message });
    }

    const db = getFirestore();
    const now = new Date().toISOString();
    const projectData = {
      ...body.data,
      userId: request.uid,
      status: 'DRAFT',
      buildStatus: 'NONE',
      lastBuildUrl: '',
      hasWatermark: true,
      createdAt: now,
      updatedAt: now,
    };

    const docRef = await db.collection('users').doc(request.uid)
      .collection('projects').add(projectData);

    return reply.status(201).send({ project: { id: docRef.id, ...projectData } });
  });

  /**
   * PUT /projects/:id
   * Update a project.
   */
  fastify.put<{ Params: { id: string } }>('/:id', { preHandler }, async (request, reply) => {
    const body = ProjectCreateSchema.partial().safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({ error: 'ValidationError', message: body.error.message });
    }

    const db = getFirestore();
    const ref = db.collection('users').doc(request.uid).collection('projects').doc(request.params.id);
    const existing = await ref.get();

    if (!existing.exists) {
      return reply.status(404).send({ error: 'NotFound', message: 'Project not found' });
    }

    await ref.update({ ...body.data, updatedAt: new Date().toISOString() });
    return reply.send({ message: 'Project updated' });
  });

  /**
   * DELETE /projects/:id
   * Delete a project.
   */
  fastify.delete<{ Params: { id: string } }>('/:id', { preHandler }, async (request, reply) => {
    const db = getFirestore();
    await db.collection('users').doc(request.uid)
      .collection('projects').doc(request.params.id).delete();
    return reply.send({ message: 'Project deleted' });
  });

  /**
   * POST /projects/:id/duplicate
   * Clone a project.
   */
  fastify.post<{ Params: { id: string } }>('/:id/duplicate', { preHandler }, async (request, reply) => {
    const db = getFirestore();
    const original = await db.collection('users').doc(request.uid)
      .collection('projects').doc(request.params.id).get();

    if (!original.exists) {
      return reply.status(404).send({ error: 'NotFound', message: 'Project not found' });
    }

    const now = new Date().toISOString();
    const data = original.data()!;
    const copy = {
      ...data,
      name: `${data.name} (Copy)`,
      status: 'DRAFT',
      buildStatus: 'NONE',
      lastBuildUrl: '',
      createdAt: now,
      updatedAt: now,
    };

    const docRef = await db.collection('users').doc(request.uid).collection('projects').add(copy);
    return reply.status(201).send({ project: { id: docRef.id, ...copy } });
  });
}
