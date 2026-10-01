import type {
  ResultFormatEmphasisRange,
  ResultFormatParagraph,
  ResultFormatPresentation,
  ResultFormatStyle
} from '../types/activityResult';

export interface ResultFormatBlock {
  start: number;
  end: number;
  text: string;
  segments: ResultFormatSegment[];
}

export interface ResultFormatSegment {
  text: string;
  style: '' | 'BOLD' | 'ITALIC' | 'BOLD ITALIC';
}

export function codePoints(value: string) {
  return Array.from(value);
}

export function defaultPresentation(summaryText: string): ResultFormatPresentation {
  return {
    paragraphs: [{ start: 0, end: codePoints(summaryText).length, style: 'NORMAL' }],
    emphasisRanges: []
  };
}

export function clonePresentation(presentation: ResultFormatPresentation) {
  return {
    paragraphs: presentation.paragraphs.map(paragraph => ({ ...paragraph })),
    emphasisRanges: presentation.emphasisRanges.map(range => ({ ...range }))
  } satisfies ResultFormatPresentation;
}

export function formatBlocks(summaryText: string, presentation: ResultFormatPresentation | null): ResultFormatBlock[] {
  const characters = codePoints(summaryText);
  const value = presentation ?? defaultPresentation(summaryText);
  return value.paragraphs.map(paragraph => {
    const emphasis = value.emphasisRanges.filter(range => range.start < paragraph.end && range.end > paragraph.start);
    const boundaries = [...new Set([
      paragraph.start,
      paragraph.end,
      ...emphasis.flatMap(range => [Math.max(range.start, paragraph.start), Math.min(range.end, paragraph.end)])
    ])].sort((left, right) => left - right);
    const segments = boundaries.slice(0, -1).map((start, index) => {
      const end = boundaries[index + 1];
      const styles = emphasis.filter(range => start >= range.start && end <= range.end).map(range => range.style);
      const style = styles.includes('BOLD') && styles.includes('ITALIC')
        ? 'BOLD ITALIC'
        : styles[0] ?? '';
      return { text: characters.slice(start, end).join(''), style } satisfies ResultFormatSegment;
    });
    return { start: paragraph.start, end: paragraph.end, text: characters.slice(paragraph.start, paragraph.end).join(''), segments };
  });
}

export function selectionToCodePointRange(root: HTMLElement, summaryText: string) {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed || !root.contains(selection.anchorNode) || !root.contains(selection.focusNode)) {
    return null;
  }
  const range = selection.getRangeAt(0).cloneRange();
  if (range.collapsed) return null;
  const textOffset = (container: Node, offset: number) => {
    let total = 0;
    let found = false;
    const textLength = (node: Node): number => node.nodeType === Node.TEXT_NODE
      ? node.textContent?.length ?? 0
      : Array.from(node.childNodes).reduce((sum, child) => sum + textLength(child), 0);
    const walk = (node: Node) => {
      if (found) return;
      if (node === container) {
        if (node.nodeType === Node.TEXT_NODE) {
          total += offset;
        } else {
          Array.from(node.childNodes).slice(0, offset).forEach(child => { total += textLength(child); });
        }
        found = true;
        return;
      }
      if (node.nodeType === Node.TEXT_NODE) {
        total += textLength(node);
        return;
      }
      Array.from(node.childNodes).forEach(walk);
    };
    walk(root);
    return found ? total : -1;
  };
  const startUtf16 = textOffset(range.startContainer, range.startOffset);
  const endUtf16 = textOffset(range.endContainer, range.endOffset);
  const toCodePoint = (value: string) => Array.from(value).length;
  const summaryUtf16 = summaryText.length;
  if (startUtf16 < 0 || endUtf16 > summaryUtf16) return null;
  return { start: toCodePoint(summaryText.slice(0, startUtf16)), end: toCodePoint(summaryText.slice(0, endUtf16)) };
}

export function paragraphRangesWithSelection(
  summaryText: string,
  presentation: ResultFormatPresentation,
  start: number,
  end: number
): ResultFormatParagraph[] {
  const length = codePoints(summaryText).length;
  const boundaries = [...new Set([
    0,
    length,
    ...presentation.paragraphs.flatMap(paragraph => [paragraph.start, paragraph.end]),
    start,
    end
  ])].filter(value => value >= 0 && value <= length).sort((left, right) => left - right);
  return boundaries.slice(0, -1).map((value, index) => ({ start: value, end: boundaries[index + 1], style: 'NORMAL' }));
}

export function toggleEmphasis(
  ranges: ResultFormatEmphasisRange[],
  start: number,
  end: number,
  style: ResultFormatStyle
) {
  const exact = ranges.some(range => range.style === style && range.start === start && range.end === end);
  const remaining = ranges.filter(range => !(range.style === style && (range.start < end && range.end > start)));
  return exact ? remaining : [...remaining, { start, end, style }].sort((left, right) => left.start - right.start || left.end - right.end);
}
