import { describe, expect, it } from 'vitest'
import { formatAge, formatDate, formatMoney, formatNumber, formatRelativeAge } from '../lib/format'

describe('formatMoney', () => {
  it('renders a rupee amount', () => {
    expect(formatMoney(3570)).toContain('3,570')
  })

  it('renders a dash for a null amount', () => {
    // A book in production has no MRP — we show "not applicable", never a fake zero.
    expect(formatMoney(null)).toBe('—')
    expect(formatMoney(undefined)).toBe('—')
  })

  it('renders zero rather than a dash', () => {
    expect(formatMoney(0)).toContain('0')
  })
})

describe('formatNumber', () => {
  it('groups thousands', () => {
    expect(formatNumber(1567)).toContain('1,567')
  })
})

describe('formatDate', () => {
  it('returns a dash for a missing date', () => {
    expect(formatDate(null)).toBe('—')
  })

  it('formats an ISO date', () => {
    expect(formatDate('2023-06-20')).toContain('2023')
  })
})

describe('formatAge', () => {
  it('describes very recent tickets', () => {
    expect(formatAge(0)).toBe('just now')
    expect(formatAge(5)).toBe('5h ago')
    expect(formatAge(52)).toBe('2d ago')
  })
})

describe('formatRelativeAge', () => {
  it('switches from hours to days', () => {
    expect(formatRelativeAge(10)).toBe('10h')
    expect(formatRelativeAge(72)).toBe('3d')
  })
})
