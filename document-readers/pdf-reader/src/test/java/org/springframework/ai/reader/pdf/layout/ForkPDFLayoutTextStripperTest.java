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

import java.lang.reflect.Method;

import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for GitHub issue #5829.
 * <p>
 * Guards against division-by-zero and overflow in
 * {@code getNumberOfNewLinesFromPreviousTextPosition} when parsing whitespace with height
 * == 0.0 and y-offset > 5.5 pts. Previously, POSITIVE_INFINITY cast to Integer.MAX_VALUE
 * drove multi-hundred-GB allocation causing OOM.
 *
 * @author Anurag Saxena
 */
class ForkPDFLayoutTextStripperTest {

	private static final int MAX_NEW_LINES_PER_POSITION_GAP = 1000;

	private ForkPDFLayoutTextStripper stripper;

	@BeforeEach
	void setUp() throws Exception {
		this.stripper = new ForkPDFLayoutTextStripper();
	}

	/**
	 * Calls getNumberOfNewLinesFromPreviousTextPosition and then mirrors what
	 * iterateThroughTextList does: sets previousTextPosition = current. This keeps the
	 * stripper state consistent across the internal state that drives the method's
	 * branching logic.
	 */
	private int invokeAndAdvance(TextPosition current) throws Exception {
		Method getMethod = ForkPDFLayoutTextStripper.class
			.getDeclaredMethod("getNumberOfNewLinesFromPreviousTextPosition", TextPosition.class);
		getMethod.setAccessible(true);
		int result = (int) getMethod.invoke(this.stripper, current);
		Method setMethod = ForkPDFLayoutTextStripper.class.getDeclaredMethod("setPreviousTextPosition",
				TextPosition.class);
		setMethod.setAccessible(true);
		setMethod.invoke(this.stripper, current);
		return result;
	}

	@Test
	void getNumberOfNewLines_firstCall_returnsOne() throws Exception {
		// When there is no previous text position, method returns 1
		int result = invokeAndAdvance(mockPos(100f, 10f, 0f));
		assertEquals(1, result, "First call with null previous should return 1");
	}

	@Test
	void getNumberOfNewLines_zeroHeight_guardsAgainstDivisionByZero() throws Exception {
		// Regression for issue #5829: height == 0.0 with y-offset > 5.5
		// Previously caused Double.POSITIVE_INFINITY cast to Integer.MAX_VALUE
		// which drove multi-hundred-GB allocation → OOM
		invokeAndAdvance(mockPos(100f, 10f, 0f)); // sets previous

		int result = invokeAndAdvance(mockPos(200f, 0.0f, 0f)); // gap=100 > 5.5, height=0
		// Guard: !Double.isFinite(0.0) is false, but 0.0 <= 0.0 → returns 1
		assertEquals(1, result, "Zero height should return 1 without division");
	}

	@Test
	void getNumberOfNewLines_negativeHeight_returnsOne() throws Exception {
		invokeAndAdvance(mockPos(100f, 10f, 0f));

		// gap=100 > 5.5, but height <= 0 → guard returns 1
		int result = invokeAndAdvance(mockPos(200f, -5.0f, 0f));
		assertEquals(1, result, "Negative height should return 1");
	}

	@Test
	void getNumberOfNewLines_NaNHeight_returnsOne() throws Exception {
		invokeAndAdvance(mockPos(100f, 10f, 0f));

		// gap=100 > 5.5, but !Double.isFinite(NaN) → guard returns 1
		int result = invokeAndAdvance(mockPos(200f, Float.NaN, 0f));
		assertEquals(1, result, "NaN height should return 1");
	}

	@Test
	void getNumberOfNewLines_positiveInfinityHeight_returnsOne() throws Exception {
		invokeAndAdvance(mockPos(100f, 10f, 0f));

		// gap=100 > 5.5, but !Double.isFinite(+∞) → guard returns 1
		int result = invokeAndAdvance(mockPos(200f, Float.POSITIVE_INFINITY, 0f));
		assertEquals(1, result, "POSITIVE_INFINITY height should return 1");
	}

	@Test
	void getNumberOfNewLines_numberOfLinesCappedToMaxValue() throws Exception {
		// gap=100, height=0.001 → floor(100/0.001)=100000
		// min(99999, 1000)=1000 → max(1, 1000)=1000
		invokeAndAdvance(mockPos(100f, 10f, 0f));

		int result = invokeAndAdvance(mockPos(200f, 0.001f, 0f));
		assertEquals(MAX_NEW_LINES_PER_POSITION_GAP, result,
				"Very large numberOfLines should be capped to MAX_NEW_LINES_PER_POSITION_GAP");
	}

	@Test
	void getNumberOfNewLines_normalOperation_returnsCorrectLineCount() throws Exception {
		// gap=50, height=10 → floor(50/10)=5
		// max(1, min(5-1, 1000)) = max(1, 4) = 4
		invokeAndAdvance(mockPos(100f, 10f, 0f));

		int result = invokeAndAdvance(mockPos(150f, 10f, 0f));
		assertEquals(4, result, "Normal operation should return correct line count");
	}

	@Test
	void getNumberOfNewLines_yOffsetBelowThreshold_returnsZero() throws Exception {
		// gap=4 ≤ 5.5 → should return 0 (y-offset check fails before height check)
		invokeAndAdvance(mockPos(100f, 10f, 0f));

		TextPosition closePos = mock(TextPosition.class);
		when(closePos.getY()).thenReturn(104f); // gap=4, ≤ 5.5
		when(closePos.getHeight()).thenReturn(0.0f); // zero height — shouldn't matter
		when(closePos.getX()).thenReturn(0f);

		int result = invokeAndAdvance(closePos);
		assertEquals(0, result, "Y-offset below threshold should return 0");
	}

	private TextPosition mockPos(float y, float height, float x) {
		TextPosition pos = mock(TextPosition.class);
		when(pos.getY()).thenReturn(y);
		when(pos.getHeight()).thenReturn(height);
		when(pos.getX()).thenReturn(x);
		return pos;
	}

}