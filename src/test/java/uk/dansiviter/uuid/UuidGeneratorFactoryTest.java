/*
 * Copyright 2022 Daniel Siviter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.dansiviter.uuid;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UuidGeneratorFactory}.
 */
class UuidGeneratorFactoryTest {
	@Test
	void type1() {
		var uuid = UuidGeneratorFactory.type1(false).get();

		assertThat(uuid.version(), is(1));
		assertThat(uuid.variant(), is(2));
	}

	@Test
	void type4() {
		var uuid = UuidGeneratorFactory.type4().get();

		assertThat(uuid.version(), is(4));
	}

	@Test
	void type6() throws InterruptedException {
		var generator = UuidGeneratorFactory.type6();
		var uuid0 = generator.get();

		assertThat(uuid0.version(), is(6));
		assertThat(uuid0.variant(), is(2));

		var uuids = new ArrayList<UUID>();

		do {
			uuids.add(generator.get());
			Thread.sleep(0, 5);  // prevent clock seq exhaustion
		} while (uuids.size() < 5_000);

		var strings = uuids.stream().map(Object::toString).collect(Collectors.toList());
		Collections.sort(strings, String.CASE_INSENSITIVE_ORDER);

		for (int i = 0; i < uuids.size(); i++) {
			assertThat("Failure at " + i, strings.get(i), is(uuids.get(i).toString()));
		}

		assertThat(Set.copyOf(strings), hasSize(uuids.size()));  // ensure distinct
	}
}
