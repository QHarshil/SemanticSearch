# UI

React frontend for the Semantic Search service. Built with Vite and plain CSS.

## Development

```bash
npm install
npm run dev
```

The dev server runs on port 5173 and proxies `/api` and `/actuator` to
`http://localhost:8080`, so start the backend first:

```bash
# from the repository root
SECURITY_AUTH_ENABLED=false SPRING_PROFILES_ACTIVE=local \
  ELASTICSEARCH_STUB_ENABLED=true ./mvnw spring-boot:run
```

## Build

```bash
npm run build
```

`vite.config.js` writes the output to `../src/main/resources/static`, so a
rebuild changes files under `src/main/resources/static/`. Those built assets are
committed on purpose: it lets `java -jar` serve the UI without requiring Node.
Commit them together with the source change that produced them.

## Layout

| Path | Contents |
| --- | --- |
| `src/lib/api.js` | Every backend call. Endpoint paths are defined only here. |
| `src/pages/` | Route components, one per route in `App.jsx` |
| `src/components/` | Shared components |

Auth is handled at the HTTP layer by Spring Security (basic auth), not in the
client. Run the backend with `SECURITY_AUTH_ENABLED=false` for local
development.
