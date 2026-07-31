/**
 * Every backend call goes through this module so endpoint paths live in one
 * place. Paths mirror the controllers under src/main/java/.../controller.
 */
const BASE = '/api/v1';

async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { Accept: 'application/json', ...(options.headers || {}) },
    ...options,
  });

  if (response.status === 401 || response.status === 403) {
    throw new Error(
      'Not authorized. Set SECURITY_AUTH_ENABLED=false for the local demo, or sign in with the configured credentials.'
    );
  }

  if (!response.ok) {
    // The API returns a JSON body with a "message" field for handled errors.
    const detail = await response
      .clone()
      .json()
      .then((body) => body.message || body.error)
      .catch(() => null);
    throw new Error(detail || `Request failed with status ${response.status}`);
  }

  return response.status === 204 ? null : response.json();
}

/**
 * Score floor the UI starts from. Mirrors SearchRequest.DEFAULT_MIN_SCORE, which
 * documents how the value was measured; keep the two in step.
 */
export const DEFAULT_MIN_SCORE = 0.2;

/**
 * GET /api/v1/search. Used instead of POST /api/v1/search/advanced because the
 * GET route is the one SecurityConfig leaves open when auth is enabled.
 */
export function search({ query, limit = 10, minScore = DEFAULT_MIN_SCORE }) {
  const params = new URLSearchParams({
    query,
    limit: String(limit),
    minScore: String(minScore),
  });
  return request(`${BASE}/search?${params}`);
}

/** GET /api/v1/documents. Returns a Spring Data page, not a bare array. */
export async function listDocuments({ page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const body = await request(`${BASE}/documents?${params}`);
  return { documents: body.content ?? [], totalElements: body.totalElements ?? 0 };
}

export function createDocument({ title, content, metadata = {} }) {
  return request(`${BASE}/documents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, content, metadata }),
  });
}

export function deleteDocument(id) {
  return request(`${BASE}/documents/${id}`, { method: 'DELETE' });
}

/** POST /api/v1/documents/seed. Loads the demo corpus the eval harness uses. */
export function seedDemoDocuments() {
  return fetch(`${BASE}/documents/seed`, { method: 'POST' }).then((r) => r.ok);
}

/** Actuator, not /api/v1 — there is no health endpoint under the API prefix. */
export async function checkHealth() {
  try {
    const response = await fetch('/actuator/health');
    return response.ok;
  } catch {
    return false;
  }
}
