import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { RatingBadge, RatingInput } from './Rating'

describe('RatingInput', () => {
  it('reports a numeric rating', async () => {
    const onChange = vi.fn()
    render(<RatingInput rating={null} ratingBeyondScale={false} onChange={onChange} />)

    await userEvent.click(screen.getByLabelText('7 out of 10'))

    expect(onChange).toHaveBeenCalledWith({ rating: 7, ratingBeyondScale: false })
  })

  it('treats ∞ as its own tier with no number', async () => {
    const onChange = vi.fn()
    render(<RatingInput rating={9} ratingBeyondScale={false} onChange={onChange} />)

    await userEvent.click(screen.getByLabelText('Beyond the scale (∞)'))

    expect(onChange).toHaveBeenCalledWith({ rating: null, ratingBeyondScale: true })
  })

  it('can clear the rating and marks the current choice', async () => {
    const onChange = vi.fn()
    render(<RatingInput rating={9} ratingBeyondScale={false} onChange={onChange} />)

    expect(screen.getByLabelText('9 out of 10')).toBeChecked()
    await userEvent.click(screen.getByLabelText('No rating'))
    expect(onChange).toHaveBeenCalledWith({ rating: null, ratingBeyondScale: false })
  })
})

describe('RatingBadge', () => {
  it('distinguishes unrated, numeric and beyond-scale', () => {
    const { rerender } = render(<RatingBadge rating={null} ratingBeyondScale={false} />)
    expect(screen.getByText('Unrated')).toBeInTheDocument()

    rerender(<RatingBadge rating={6} ratingBeyondScale={false} />)
    expect(screen.getByLabelText('Rated 6 out of 10')).toBeInTheDocument()

    rerender(<RatingBadge rating={null} ratingBeyondScale />)
    expect(screen.getByLabelText('Rated beyond the 1 to 10 scale')).toBeInTheDocument()
  })
})
