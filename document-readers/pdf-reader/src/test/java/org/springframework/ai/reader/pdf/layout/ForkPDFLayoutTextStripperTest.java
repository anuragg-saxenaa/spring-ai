/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.reader.pdf.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ForkPDFLayoutTextStripper}.
 *
 * @author Anurag Saxena
 */
class ForkPDFLayoutTextStripperTest {

	/**
	 * Verifies that zero-height TextPositions followed by large y-axis offsets do not
	 * cause a division-by-zero OOM. Regression test for
	 * https://github.com/spring-projects/spring-ai/issues/5829
	 *
	 * When height == 0.0 and (textY - prevY) > 5.5, dividing by height yields
	 * Double.POSITIVE_INFINITY which cast to int 2147483646, allocating ~400 GiB char[].
	 */
	@Test
	void testGetNumberOfNewlinesWithWhitespace_ZeroHeight_returnsZero() throws Exception {
		ForkPDFLayoutTextStripper stripper = new ForkPDFLayoutTextStripper();
		stripper.setSortByPosition(true);

		// Mock TextPosition with zero height — simulates whitespace-only glyphs
		TextPosition prev = new TextPositionMock(0, 100f, 10f);   // prevY=100, height=10
		TextPosition curr = new TextPositionMock(0, 200f, 0f);    // currY=200, height=0 (whitespace)

		// Verify: deltaY = 100 > 5.5, but height == 0.0
		// Before fix: (100.0 / 0.0) = Infinity → cast to int 2147483646 → OOM
		// After fix: returns 0 immediately (no OOM)
		int newlines = stripper.getNumberOfNewlinesWithWhitespace(curr, prev);
		assertEquals(0, newlines, "Zero-height position should return 0 to avoid division by zero");
	}

	/**
	 * Verifies the guard doesn't break the normal (non-zero height) case.
	 */
	@Test
	void testGetNumberOfNewlinesWithWhitespace_normalHeight_returnsExpected() throws Exception {
		ForkPDFLayoutTextStripper stripper = new ForkPDFLayoutTextStripper();
		stripper.setSortByPosition(true);

		TextPosition prev = new TextPositionMock(0, 100f, 10f);   // prevY=100, height=10
		TextPosition curr = new TextPositionMock(0, 112f, 6f);    // currY=112, height=6, delta=12

		int newlines = stripper.getNumberOfNewlinesWithWhitespace(curr, prev);
		// deltaY=12, height=6 → floor(12/6)=2 lines, max(1, 2-1)=1
		assertEquals(1, newlines);
	}

	/**
	 * Verifies zero-height positions below the 5.5pt threshold don't trigger the guard.
	 */
	@Test
	void testGetNumberOfNewlinesWithWhitespace_zeroHeightBelowThreshold_returnsZero() throws Exception {
		ForkPDFLayoutTextStripper stripper = new ForkPDFLayoutTextStripper();
		stripper.setSortByPosition(true);

		TextPosition prev = new TextPositionMock(0, 100f, 10f);
		TextPosition curr = new TextPositionMock(0, 105f, 0f); // deltaY=5, not > 5.5

		int newlines = stripper.getNumberOfNewlinesWithWhitespace(curr, prev);
		assertEquals(0, newlines);
	}

	/**
	 * Minimal mock for {@link org.apache.pdfbox.text.TextPosition}.
	 * PDFBox TextPosition is final and requires a PDDocument, so we simulate it here.
	 */
	private static final class TextPositionMock extends TextPosition {
		private final float y;
		private final float height;

		TextPositionMock(int pageNumber, float y, float height) {
			// All params passed to super to satisfy constructor
			super(pageNumber, 0f, y, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, null);
			this.y = y;
			this.height = height;
		}

		@Override
		public float getY() {
			return y;
		}

		@Override
		public float getHeightDir() {
			return height;
		}
	}
}