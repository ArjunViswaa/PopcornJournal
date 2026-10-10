import { expect, test, type Page } from '@playwright/test'

// Uses a unique title so the journey works whatever is already in the e2e database.
const title = `E2E Screening ${Date.now()}`

async function uniqueTitleCount(page: Page): Promise<number> {
  await page.goto('/')
  const tile = page.locator('div.card', { hasText: 'Unique titles watched' })
  const empty = page.getByText('Nothing in the journal yet')
  await expect(tile.or(empty)).toBeVisible()
  if (await empty.isVisible()) return 0
  const text = (await tile.locator('p').nth(1).textContent()) ?? '0'
  return Number(text.replace(/[^\d]/g, ''))
}

test('add, find, log, edit and delete a movie', async ({ page }) => {
  // 1-2. dashboard is up and showing database numbers
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Your film journal' })).toBeVisible()
  const before = await uniqueTitleCount(page)

  // add a movie with no review yet
  await page.getByRole('link', { name: 'Add movie' }).first().click()
  await page.getByLabel(/^Title/).fill(title)
  await page.getByLabel(/Release year/).fill('2024')
  await page.getByLabel(/^Language/).fill('Tamil')
  await page.getByLabel('7 out of 10').first().check({ force: true })
  await page.getByRole('button', { name: 'Add to journal' }).click()
  await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible()
  const detailUrl = page.url()

  // 3-4. library search and filters find it
  await page.goto('/movies')
  await page.getByLabel('Search by title').fill(title)
  await expect(page.getByRole('link', { name: `${title} (2024)` })).toBeVisible()
  await page.getByLabel('Language').selectOption('Tamil')
  await expect(page.getByRole('link', { name: `${title} (2024)` })).toBeVisible()
  await page.getByLabel('Minimum rating').selectOption('9')
  await expect(page.getByText('No titles match')).toBeVisible()
  await page.getByLabel('Minimum rating').selectOption('')

  // 5-6. open it and log a viewing
  await page.getByRole('link', { name: `${title} (2024)` }).click()
  await page.getByRole('button', { name: 'Log a viewing' }).click()
  const dialog = page.getByRole('dialog', { name: 'Log a viewing' })
  await dialog.getByLabel(/Watched on/).fill('2026-03-21')
  await dialog.getByLabel(/^How/).selectOption('STREAMING')
  await dialog.getByLabel(/Platform or service/).fill('Netflix')
  await dialog.getByRole('button', { name: 'Add viewing' }).click()
  await expect(page.getByText('1 viewing', { exact: true })).toBeVisible()
  await expect(page.getByText('Streaming · Netflix')).toBeVisible()

  // 7. edit the rating and write the review
  await page.getByRole('link', { name: 'Edit' }).click()
  await page.getByLabel('9 out of 10').check({ force: true })
  await page.getByLabel(/^Review/).fill('Better than I remembered.')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByText('Changes saved')).toBeVisible()

  // 8. values survive a full reload, i.e. they came back from PostgreSQL
  await page.goto(detailUrl)
  await page.reload()
  await expect(page.getByText('Better than I remembered.')).toBeVisible()
  await expect(page.getByLabel('Rated 9 out of 10').first()).toBeVisible()
  await expect(page.getByText('21 Mar 2026')).toBeVisible()

  // 9. the dashboard counts the new title
  expect(await uniqueTitleCount(page)).toBe(before + 1)

  // clean up through the UI, including the confirmation step
  await page.goto(detailUrl)
  await page.getByRole('button', { name: 'Delete', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Delete movie' }).click()
  await expect(page).toHaveURL(/\/movies$/)
  expect(await uniqueTitleCount(page)).toBe(before)
})
