import { describe, expect, it } from 'vitest';
import { defaultPresentation, formatBlocks, paragraphRangesWithSelection, toggleEmphasis } from './resultFormat';

describe('result format presentation mapping', () => {
  it('maps backend ranges to renderable paragraphs and segments', () => {
    const presentation = {
      paragraphs: [{ start: 0, end: 5, style: 'NORMAL' as const }],
      emphasisRanges: [{ start: 1, end: 3, style: 'BOLD' as const }]
    };

    expect(formatBlocks('A😀BCD', presentation)).toEqual([{
      start: 0,
      end: 5,
      text: 'A😀BCD',
      segments: [
        { text: 'A', style: '' },
        { text: '😀B', style: 'BOLD' },
        { text: 'CD', style: '' }
      ]
    }]);
  });

  it('uses one normal paragraph when no overlay exists', () => {
    expect(defaultPresentation('标题😀摘要')).toEqual({
      paragraphs: [{ start: 0, end: 5, style: 'NORMAL' }],
      emphasisRanges: []
    });
  });

  it('keeps paragraph and emphasis mutations in the presentation model', () => {
    const base = defaultPresentation('first second');
    const withParagraph = paragraphRangesWithSelection('first second', base, 0, 5);
    expect(withParagraph).toEqual([
      { start: 0, end: 5, style: 'NORMAL' },
      { start: 5, end: 12, style: 'NORMAL' }
    ]);
    expect(toggleEmphasis([], 0, 5, 'ITALIC')).toEqual([{ start: 0, end: 5, style: 'ITALIC' }]);
    expect(toggleEmphasis([{ start: 0, end: 5, style: 'ITALIC' }], 0, 5, 'ITALIC')).toEqual([]);
  });
});
