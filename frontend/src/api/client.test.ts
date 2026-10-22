import { describe, expect, it } from 'vitest'
import { buildQuery } from '../api/client'

describe('buildQuery', () => {
  it('omits empty and null values', () => {
    expect(buildQuery({ status: 'OPEN', category: '', priority: undefined, page: 0 })).toBe(
      '?status=OPEN&page=0',
    )
  })

  it('returns an empty string when nothing is set', () => {
    expect(buildQuery({ a: undefined, b: null, c: '' })).toBe('')
  })

  it('encodes values', () => {
    expect(buildQuery({ search: 'royalty & payments' })).toBe(
      '?search=royalty+%26+payments',
    )
  })
})
