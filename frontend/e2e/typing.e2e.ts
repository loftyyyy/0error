import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const DRILL = '[data-testid="drill-text"]'

async function drillText(page: Page): Promise<string> {
  const text = await page.locator(DRILL).innerText()
  return text.replace(/\u00a0/g, ' ')
}

async function typeText(page: Page, text: string) {
  for (const character of text) {
    await page.keyboard.press(character === ' ' ? 'Space' : character)
  }
}

test('a guest finishes one drill through the browser', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator(DRILL)).toBeVisible()

  await typeText(page, await drillText(page))

  await expect(
    page.getByRole('heading', { name: /Gate passed\.|Not yet\./ }),
  ).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('Recent drills')).toBeVisible({ timeout: 10_000 })
})

test('a burst of keys in one task still scores', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator(DRILL)).toBeVisible()
  const text = await drillText(page)

  await page.locator(DRILL).click()
  await page.evaluate((drill) => {
    for (const character of drill) {
      window.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: character === ' ' ? ' ' : character,
          bubbles: true,
          cancelable: true,
        }),
      )
    }
  }, text)

  await expect(
    page.getByRole('heading', { name: /Gate passed\.|Not yet\./ }),
  ).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('The drill is not scored yet.')).toBeHidden()
})

test('a reload resumes the open drill and start over clears it', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator(DRILL)).toBeVisible()
  const text = await drillText(page)

  const sent = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/sessions/') &&
      response.url().includes('/telemetry') &&
      response.status() === 200,
    { timeout: 15_000 },
  )
  await typeText(page, text.slice(0, 6))
  await sent

  await page.reload()
  await expect(page.getByText('Picked up where you left off.')).toBeVisible()
  await expect(page.getByTestId('drill-text')).toHaveText(text.replace(/ /g, '\u00a0'))

  const abandoned = page.waitForResponse(
    (response) => response.url().includes('/abandon') && response.status() === 204,
  )
  await page.getByRole('button', { name: 'Start over' }).click()
  await abandoned

  await expect(page.getByText('Picked up where you left off.')).toBeHidden()
  await expect(page.getByTestId('drill-text')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Start over' })).toBeHidden()
})
