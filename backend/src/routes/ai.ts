import { FastifyInstance } from 'fastify';
import { verifyFirebaseToken } from '../middleware/auth';
import { generateAppWithAI } from '../services/aiService';
import { z } from 'zod';

const GenerateSchema = z.object({
  prompt: z.string().min(10).max(2000),
  templateHint: z.string().optional(),
});

export async function aiRoutes(fastify: FastifyInstance): Promise<void> {
  /**
   * POST /ai/generate
   * Uses AI to generate an app structure from a natural language prompt.
   * Rate limited to 10 requests/min per user.
   */
  fastify.post('/generate', {
    preHandler: [verifyFirebaseToken],
    config: {
      rateLimit: {
        max: 10,
        timeWindow: '1 minute',
      },
    },
  }, async (request, reply) => {
    const body = GenerateSchema.safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({ error: 'ValidationError', message: body.error.message });
    }

    try {
      const result = await generateAppWithAI(body.data.prompt, body.data.templateHint);
      return reply.send(result);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'AI generation failed';
      fastify.log.error(err, 'AI generation error');
      return reply.status(500).send({ error: 'AIError', message });
    }
  });

  /**
   * POST /ai/suggest
   * Returns smart UX suggestions for an existing project.
   */
  fastify.post<{ Body: { projectId: string; currentSections: string[] } }>(
    '/suggest',
    { preHandler: [verifyFirebaseToken] },
    async (request, reply) => {
      const { currentSections } = request.body;
      // Simple heuristic suggestions — extend with actual AI call
      const suggestions: string[] = [];
      if (!currentSections.includes('CONTACT')) suggestions.push('Add a Contact section to increase conversions');
      if (!currentSections.includes('TESTIMONIALS')) suggestions.push('Testimonials boost trust — consider adding one');
      if (!currentSections.includes('HERO')) suggestions.push('A Hero section should be your first impression');
      return reply.send({ suggestions });
    }
  );
}
