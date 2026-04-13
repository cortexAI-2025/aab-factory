import OpenAI from 'openai';

let openaiClient: OpenAI | null = null;

function getOpenAI(): OpenAI {
  if (!openaiClient) {
    openaiClient = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
  }
  return openaiClient;
}

interface GeneratedApp {
  appName: string;
  description: string;
  config: {
    appName: string;
    packageName: string;
    primaryColor: string;
    accentColor: string;
    backgroundColor: string;
    logoUrl: string;
    versionName: string;
    versionCode: number;
    minSdkVersion: number;
    targetSdkVersion: number;
  };
  sections: Array<{
    id: string;
    type: string;
    title: string;
    subtitle: string;
    content: string;
    imageUrl: string;
    ctaText: string;
    ctaAction: string;
    isVisible: boolean;
    order: number;
  }>;
  suggestedTemplate: string | null;
}

const SYSTEM_PROMPT = `You are an expert Android app architect. Given a user's app description, generate a complete app structure in JSON format.

Return ONLY valid JSON with this exact structure:
{
  "appName": "string (max 30 chars)",
  "description": "string (max 200 chars)",
  "config": {
    "appName": "same as above",
    "packageName": "com.example.appname (valid Android package)",
    "primaryColor": "#RRGGBB",
    "accentColor": "#RRGGBB",
    "backgroundColor": "#RRGGBB",
    "logoUrl": "",
    "versionName": "1.0.0",
    "versionCode": 1,
    "minSdkVersion": 26,
    "targetSdkVersion": 34
  },
  "sections": [
    {
      "id": "uuid-here",
      "type": "HERO|FEATURES|GALLERY|TESTIMONIALS|CONTACT|PRODUCTS|BLOG|MAP|FORM|FOOTER",
      "title": "string",
      "subtitle": "string",
      "content": "string",
      "imageUrl": "",
      "ctaText": "string",
      "ctaAction": "",
      "isVisible": true,
      "order": 0
    }
  ],
  "suggestedTemplate": "business-profile|ecommerce|blog|lead-generation|local-services|null"
}

Rules:
- Choose colors that match the app's purpose and look professional
- Generate 3-6 relevant sections
- Make section content realistic and relevant to the prompt
- Keep all text concise and professional
- packageName must be a valid Java package (lowercase, dots, no spaces)`;

/**
 * Generates an app structure from a natural language prompt using OpenAI or Mistral.
 */
export async function generateAppWithAI(
  prompt: string,
  templateHint?: string
): Promise<GeneratedApp> {
  const provider = process.env.AI_PROVIDER ?? 'openai';

  if (provider === 'mistral') {
    return generateWithMistral(prompt, templateHint);
  }

  return generateWithOpenAI(prompt, templateHint);
}

async function generateWithOpenAI(
  prompt: string,
  _templateHint?: string
): Promise<GeneratedApp> {
  const client = getOpenAI();

  const response = await client.chat.completions.create({
    model: 'gpt-4o',
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: `Create an Android app: ${prompt}` },
    ],
    response_format: { type: 'json_object' },
    temperature: 0.7,
    max_tokens: 2000,
  });

  const content = response.choices[0]?.message?.content;
  if (!content) throw new Error('No response from AI');

  const parsed = JSON.parse(content) as GeneratedApp;
  // Assign UUIDs to sections
  parsed.sections = parsed.sections.map((s, i) => ({
    ...s,
    id: crypto.randomUUID(),
    order: i,
  }));

  return parsed;
}

async function generateWithMistral(
  prompt: string,
  _templateHint?: string
): Promise<GeneratedApp> {
  // Mistral API call via their SDK or raw HTTP
  const response = await fetch('https://api.mistral.ai/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${process.env.MISTRAL_API_KEY}`,
    },
    body: JSON.stringify({
      model: 'codestral-latest',
      messages: [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: `Create an Android app: ${prompt}` },
      ],
      response_format: { type: 'json_object' },
      temperature: 0.7,
      max_tokens: 2000,
    }),
  });

  if (!response.ok) {
    throw new Error(`Mistral API error: ${response.statusText}`);
  }

  const data = (await response.json()) as { choices: Array<{ message: { content: string } }> };
  const content = data.choices[0]?.message?.content;
  if (!content) throw new Error('No response from Mistral');

  const parsed = JSON.parse(content) as GeneratedApp;
  parsed.sections = parsed.sections.map((s, i) => ({
    ...s,
    id: crypto.randomUUID(),
    order: i,
  }));

  return parsed;
}
