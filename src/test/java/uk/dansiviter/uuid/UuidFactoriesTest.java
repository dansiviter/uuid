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

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UuidFactories}.
 */
class UuidFactoriesTest {
	@Test
	void type1_random() {
		var uuid = UuidFactories.type1().get();

		assertThat(uuid.version(), is(1));
		assertThat(uuid.variant(), is(2));
	}

	@Test
	void type1_macAddress() {
		var uuid = UuidFactories.type1(false).get();

		assertThat(uuid.version(), is(1));
		assertThat(uuid.variant(), is(2));
	}

	@Test
	void type4() {
		var uuid = UuidFactories.type4().get();

		assertThat(uuid.version(), is(4));
	}

	@Test
	void type6_random() throws InterruptedException {
		var generator = UuidFactories.type6();
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

	@Test
	void type6_macAddress() throws InterruptedException {
		var generator = UuidFactories.type6(false);
		var uuid0 = generator.get();

		assertThat(uuid0.version(), is(6));
		assertThat(uuid0.variant(), is(2));
	}

	@Test
	void toType6() {
		var uuidV1 = UuidFactories.type1().get();
		var uuidV6 = UuidFactories.toType6(uuidV1);

		assertThat(uuidV6.version(), is(6));
		assertThat(uuidV6.variant(), is(2));
	}

	@Test
	void v1tov6_supported() {
		var uuid = randomUUID();
		assertThrows(UnsupportedOperationException.class, () -> UuidFactories.toType6(uuid));
	}
}
