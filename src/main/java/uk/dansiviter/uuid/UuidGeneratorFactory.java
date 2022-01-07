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

import static java.time.ZoneOffset.UTC;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Creates factories for generating different types of UUIDs.
 *
 * @see https://datatracker.ietf.org/doc/html/draft-peabody-dispatch-new-uuid-format
 */
public enum UuidGeneratorFactory { ;
	private static final Instant GREGORIAN_EPOCH = LocalDateTime.of(1582, 10, 15, 0, 0, 0).toInstant(UTC);
	private static final Random RAND = new SecureRandom();

	/**
	 * Creates a new factory instance supplying type 1 UUIDs using cryptographically strong random node value.
	 *
	 * @return a type 1 factory.
	 */
	public static Supplier<UUID> type1() {
		return type1(true);
	}

	/**
	 * Creates a new factory instance supplying type 1 UUIDs.
	 *
	 * @param random if {@code true} then a cryptographically strong random value will be used for the node. If
	 *   {@code false} then the MAC address will be used.
	 * @return a type 1 factory.
	 */
	public static Supplier<UUID> type1(boolean random) {
		return new Type1Supplier(
			UuidGeneratorFactory::getGregorianEpochTime,
			random ? RAND::nextLong : UuidGeneratorFactory::getMacAddress);
	}

	/**
	 * Creates a new factory instance supplying type 4 UUIDs.
	 *
	 * @return a type 4 factory.
	 * @see UUID#randomUUID()
	 */
	public static Supplier<UUID> type4() {
		return UUID::randomUUID;
	}

	/**
	 * Creates a new factory instance supplying type 6 UUIDs using cryptographically strong random node value.
	 * <p>
	 * This type of UUID is useful for when lexical ordering is important (e.g. database indexes).
	 *
	 * @return a type 6 factory.
	 */
	public static Supplier<UUID> type6() {
		return type6(true);
	}

	/**
	 * Creates a new factory instance supplying type 6 UUIDs.
	 * <p>
	 * This type of UUID is useful for when lexical ordering is important (e.g. database indexes).
	 *
	 * @param random if {@code true} then a cryptographically strong random value will be used for the node. If
	 *   {@code false} then the MAC address will be used.
	 * @return a type 6 factory.
	 */
	public static Supplier<UUID> type6(boolean random) {
		return new Type6Supplier(
				UuidGeneratorFactory::getGregorianEpochTime,
				random ? RAND::nextLong : UuidGeneratorFactory::getMacAddress);
	}

	/**
	 *
	 * @return
	 */
	public static long unixEpochTime() {
		var duration = Duration.between(Instant.EPOCH, Instant.now());
		return duration.getSeconds() * 10_000_000 + duration.getNano() / 100;
	}

	/**
	 *
	 * @return
	 */
	public static long getGregorianEpochTime() {
		var duration = Duration.between(GREGORIAN_EPOCH, Instant.now());
		return duration.getSeconds() * 10_000_000 + duration.getNano() / 100;
	}

	public static long getMacAddress() {
		try {
			var ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
			return toLong(ni.getHardwareAddress());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static long toLong(byte[] bytes) {
		var l = 0L;
		for (var b : bytes) {
			l = (l << 8) + (b & 0xFF);
		}
		return l;
	}

	/**
	 * Converts a Type 1 to Type 6 UUID.
	 *
	 * @param uuid
	 * @return
	 */
	public static UUID toType6(UUID uuid) {
		if (uuid.version() != 1) {
			throw new UnsupportedOperationException("Only v1 supported!");
		}

		var msb = uuid.getMostSignificantBits();
		msb = ((msb >> 32) & 0x0FFF) | // 12 least significant bits
				(0x6000) | // version number
				((msb >> 28) & 0x0000000FFFFF0000L) | // next 20 bits
				((msb << 20) & 0x000FFFF000000000L) | // next 16 bits
				(msb << 52); // 12 most significant bits
		return new UUID(msb, uuid.getLeastSignificantBits());
	}

	/**
	 * Base time implementation for 1 and 6 types.
	 */
	private abstract static class TimeBasedSupplier implements Supplier<UUID> {
		private static final int MAX_CLOCK_SEQ = 1 << 14;  // 14 bit
		private final AtomicInteger clockSeq = new AtomicInteger(RAND.nextInt(MAX_CLOCK_SEQ / 2));
		private final AtomicLong lastTime = new AtomicLong();
		private final AtomicLong lsb = new AtomicLong();
		private final LongSupplier time;
		private final LongSupplier node;

		private TimeBasedSupplier(LongSupplier time, LongSupplier node) {
			this.time = time;
			this.node = node;
		}

		@Override
		public UUID get() {
			var time = this.time.getAsLong();
			var lastTime = this.lastTime.getAndUpdate(v -> v != time ? time : v);
			var lsb = this.lsb.updateAndGet(v -> v == 0 || lastTime >= time ? leastSigBits() : v);
			return new UUID(mostSigBits(time), lsb);
		}

		protected abstract long mostSigBits(long time);

		private long leastSigBits() {
			var clkSeq = this.clockSeq.updateAndGet(v -> v > MAX_CLOCK_SEQ ? 0 : v + 1);

			// clk_seq_hi_res:
			// The first two bits MUST be set to the UUID variant (10) The
			// remaining 6 bits contain the high portion of the clock sequence.
			// Occupies bits 64 through 71 (octet 8)
			// clock_seq_low:
			// The 8 bit low portion of the clock sequence. Occupies bits 72
			// through 79 (octet 9)
			var clkSeqRes = ((0b10 << 14) | (clkSeq & 0x3FFF));

			// node:
			// 48 bit spatially unique identifier Occupies bits 80 through 127
			// (octets 10-15)
			var node = this.node.getAsLong();
			return (((long) clkSeqRes) << 48) | (node & 0xFFFFFFFFFFFFL);
		}
	}

	private static class Type1Supplier extends TimeBasedSupplier {
		Type1Supplier(LongSupplier time, LongSupplier node) {
			super(time, node);
		}

		@Override
		protected long mostSigBits(long time) {
			// time_low							unsigned 32		0-3		The low field of the
			// 											bit integer					timestamp
			var lo = (int) time;
			// time_mid							unsigned 16		4-5		The middle field of the
			// 											bit integer					timestamp
			var mid = (int) (time >>> 32);
			// time_hi_and_version	unsigned 16		6-7		The high field of the
			// 											bit integer					timestamp multiplexed
			// 																					with the version number
			var hi = (int) (time >>> 48);
			var hiVer = 0x1000 | (hi & 0xFFF);
			return (((long) lo) << 32) | (((mid << 16) | (hiVer & 0xFFFF)) & 0xFFFFFFFFL);
		}
	}

	private static class Type6Supplier extends TimeBasedSupplier {
		private Type6Supplier(LongSupplier time, LongSupplier node) {
			super(time, node);
		}

		@Override
		protected long mostSigBits(long time) {
			// time_high:
			// The most significant 32 bits of the 60 bit starting timestamp.
			// Occupies bits 0 through 31 (octets 0-3)
			int hi = (int) (time >>> 28);
			// time_mid:
			// The middle 16 bits of the 60 bit starting timestamp. Occupies
			// bits 32 through 47 (octets 4-5)
			var mid = (((int) time) >>> 12);
			// time_low_and_version:
			// The first four most significant bits MUST contain the UUIDv6
			// version (0110) while the remaining 12 bits will contain the least
			// significant 12 bits from the 60 bit starting timestamp. Occupies
			// bits 48 through 63 (octets 6-7)
			var loVer = 0x6000 | (time & 0xFFF);
			return (((long) hi) << 32) | (((mid << 16) | (loVer & 0xFFFF)) & 0xFFFFFFFFL);
		}
	}
}
