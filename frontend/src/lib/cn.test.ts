import { describe, expect, it } from 'vitest'
import { cn } from '../lib/cn'

describe('cn', () => {
  it('joins the truthy class names', () => {
    expect(cn('a', 'b')).toBe('a b')
  })

  it('drops falsy values', () => {
    expect(cn('a', false, undefined, null, '', 'b')).toBe('a b')
  })
})
