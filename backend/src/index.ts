import 'dotenv/config';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import { initializeFirebase } from './config/firebase';
import { authRoutes } from './routes/auth';
import { projectRoutes } from './routes/projects';
import { aiRoutes } from './routes/ai';
import { buildRoutes } from './routes/builds';
import { stripeRoutes } from './routes/stripe';

// Initialize Firebase Admin
initializeFirebase();

const fastify = Fastify({
  logger: {
    level: process.env.NODE_ENV === 'production' ? 'warn' : 'info',
    transport: process.env.NODE_ENV !== 'production'
      ? { target: 'pino-pretty', options: { colorize: true } }
      : undefined,
  },
});

// Security & CORS
fastify.register(helmet, { contentSecurityPolicy: false });
fastify.register(cors, {
  origin: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
  allowedHeaders: ['Content-Type', 'Authorization'],
});

// Rate limiting
fastify.register(rateLimit, {
  max: 100,
  timeWindow: '1 minute',
});

// Health check
fastify.get('/health', async () => ({
  status: 'ok',
  timestamp: new Date().toISOString(),
  version: '1.0.0',
}));

// API routes
fastify.register(authRoutes, { prefix: '/auth' });
fastify.register(projectRoutes, { prefix: '/projects' });
fastify.register(aiRoutes, { prefix: '/ai' });
fastify.register(buildRoutes, { prefix: '/builds' });
fastify.register(stripeRoutes, { prefix: '/stripe' });

// Global error handler
fastify.setErrorHandler((error, _request, reply) => {
  fastify.log.error(error);

  if (error.statusCode) {
    reply.status(error.statusCode).send({
      error: error.name,
      message: error.message,
    });
    return;
  }

  reply.status(500).send({
    error: 'InternalServerError',
    message: 'An unexpected error occurred',
  });
});

// Not found handler
fastify.setNotFoundHandler((_request, reply) => {
  reply.status(404).send({ error: 'NotFound', message: 'Route not found' });
});

// Start server
const start = async () => {
  try {
    const port = parseInt(process.env.PORT ?? '3000', 10);
    await fastify.listen({ port, host: '0.0.0.0' });
    fastify.log.info(`AAB Factory backend listening on port ${port}`);
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
};

start();
