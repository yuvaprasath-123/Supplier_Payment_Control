import {
  defineConfig
} from "@playwright/test";

export default defineConfig({

  testDir:
    "./e2e",

  timeout:
    120000,

  expect: {
    timeout:
      15000
  },

  /*
   * These tests use the same real backend services
   * and shared Oracle database.
   *
   * Run them sequentially to avoid test-data
   * interference between requester workflows.
   */
  fullyParallel:
    false,

  workers:
    1,

  use: {

    baseURL:
      "http://localhost:4173",

    headless:
      true,

    screenshot:
      "only-on-failure",

    trace:
      "retain-on-failure"
  },

  webServer: {

    command:
      "npm run dev -- --host localhost --port 4173",

    url:
      "http://localhost:4173",

    reuseExistingServer:
      true,

    timeout:
      120000
  }
});