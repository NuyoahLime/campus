import { describe, it, expect } from 'vitest';
import { localDateTimeToInstant, instantToLocalDateTime } from '@/utils/activity-time';

describe('activity-time', () => {
  describe('localDateTimeToInstant', () => {
    it('returns undefined for null', () => {
      expect(localDateTimeToInstant(null)).toBeUndefined();
    });

    it('returns undefined for undefined', () => {
      expect(localDateTimeToInstant(undefined)).toBeUndefined();
    });

    it('returns undefined for empty string', () => {
      expect(localDateTimeToInstant('')).toBeUndefined();
    });

    it('returns undefined for invalid date string', () => {
      expect(localDateTimeToInstant('not-a-date')).toBeUndefined();
    });

    it('converts local datetime string to ISO string', () => {
      const result = localDateTimeToInstant('2026-09-01T08:00:00');
      expect(result).toBeDefined();
      expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    });

    it('preserves UTC offset in roundtrip', () => {
      const input = '2026-09-01T08:00:00';
      const iso = localDateTimeToInstant(input);
      expect(iso).toBeDefined();
      const back = instantToLocalDateTime(iso!);
      expect(back).toBe(input);
    });

    it('handles date-only string', () => {
      const result = localDateTimeToInstant('2026-09-01');
      expect(result).toBeDefined();
      expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    });
  });

  describe('instantToLocalDateTime', () => {
    it('returns empty string for null', () => {
      expect(instantToLocalDateTime(null)).toBe('');
    });

    it('returns empty string for undefined', () => {
      expect(instantToLocalDateTime(undefined)).toBe('');
    });

    it('returns empty string for empty input', () => {
      expect(instantToLocalDateTime('')).toBe('');
    });

    it('returns empty string for invalid date string', () => {
      expect(instantToLocalDateTime('not-a-date')).toBe('');
    });

    it('converts ISO string to local datetime format', () => {
      const result = instantToLocalDateTime('2026-09-01T00:00:00.000Z');
      expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
    });

    it('zero-pads single-digit month and day', () => {
      const result = instantToLocalDateTime('2026-01-05T00:00:00.000Z');
      expect(result).toMatch(/^\d{4}-01-05T\d{2}:\d{2}:\d{2}$/);
    });

    it('zero-pads single-digit hours, minutes, seconds', () => {
      const input = '2026-09-01T00:00:00.000Z';
      const result = instantToLocalDateTime(input);
      const timePart = result.split('T')[1];
      for (const part of timePart.split(':')) {
        expect(part).toMatch(/^\d{2}$/);
      }
    });

    it('roundtrip preserves original local time', () => {
      const local = '2026-09-01T08:00:00';
      const iso = localDateTimeToInstant(local);
      const back = instantToLocalDateTime(iso!);
      expect(back).toBe(local);
    });
  });
});
