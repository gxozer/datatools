import type { Config } from "jest";

const config: Config = {
  preset: "ts-jest",
  testEnvironment: "jsdom",
  setupFilesAfterEnv: ["<rootDir>/jest.setup.ts"],
  moduleNameMapper: {
    "\\.(css|less|scss|sass)$": "<rootDir>/__mocks__/fileMock.js",
  },
  testPathIgnorePatterns: ["/node_modules/", "/src/test/e2e/"],
  transform: {
    "^.+\\.tsx?$": ["ts-jest", {
      tsconfig: {
        jsx: "react-jsx",
        esModuleInterop: true,
        types: ["jest", "@testing-library/jest-dom", "node"],
      },
    }],
  },
};

export default config;
