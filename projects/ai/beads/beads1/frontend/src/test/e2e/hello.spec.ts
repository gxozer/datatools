import { test, expect } from '@playwright/test'

test.describe('Hello World App', () => {
  test('page loads without errors', async ({ page }) => {
    const errors: string[] = []
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text())
    })

    await page.goto('/')
    await expect(page).toHaveTitle(/Hello World|Vite/)
    expect(errors).toHaveLength(0)
  })

  test('displays Hello World message from backend', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByText('Hello, World!')).toBeVisible({ timeout: 5000 })
  })

  test('heading is present', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Hello World App' })).toBeVisible()
  })

  test('loading state appears then resolves', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByText('Hello, World!')).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('Loading...')).not.toBeVisible()
  })
})
