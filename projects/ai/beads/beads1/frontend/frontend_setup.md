# Frontend Setup

This document describes the steps taken to set up the React/TypeScript frontend.

## Prerequisites

- Node.js (v18 or higher)
- npm

## 1. Scaffold with Vite

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
```

This creates a React + TypeScript project using Vite as the build tool.

## 2. Install Testing Dependencies

```bash
npm install -D jest @testing-library/react @testing-library/jest-dom @testing-library/user-event ts-jest jest-environment-jsdom @types/jest
```

| Package | Purpose |
|---------|---------|
| `jest` | Test runner |
| `@testing-library/react` | React component testing utilities |
| `@testing-library/jest-dom` | Custom DOM matchers (e.g. `toBeInTheDocument`) |
| `@testing-library/user-event` | Simulate user interactions |
| `ts-jest` | TypeScript transformer for Jest |
| `jest-environment-jsdom` | Browser-like DOM environment for tests |
| `@types/jest` | TypeScript types for Jest |

## 3. Configure Jest

**`jest.config.ts`**
```ts
import type { Config } from "jest";

const config: Config = {
  preset: "ts-jest",
  testEnvironment: "jsdom",
  setupFilesAfterEnv: ["<rootDir>/jest.setup.ts"],
  moduleNameMapper: {
    "\\.(css|less|scss|sass)$": "<rootDir>/__mocks__/fileMock.js",
  },
};

export default config;
```

**`jest.setup.ts`** — runs after the test framework is installed:
```ts
import "@testing-library/jest-dom";
```

**`__mocks__/fileMock.js`** — mocks CSS/asset imports:
```js
module.exports = '';
```

## 4. Add Test Script

In `package.json`:
```json
"scripts": {
  "test": "jest"
}
```

## 5. Running Tests

```bash
npm test
```

Test files go in `src/__tests__/` with the naming convention `*.test.tsx` or `*.test.ts`.

## Project Structure

```
frontend/
├── __mocks__/
│   └── fileMock.js        # Mocks CSS/asset imports
├── src/
│   ├── __tests__/         # Test files go here
│   ├── App.tsx
│   └── main.tsx
├── jest.config.ts
├── jest.setup.ts
├── package.json
└── vite.config.ts
```
