/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.util.Objects;

import org.reactivestreams.Subscriber;
import reactor.core.Fuseable;
import reactor.core.Receiver;
import reactor.core.Trackable;

/**
 * A Stream that emits only one value and then complete.
 * <p>
 * Since the fluxion retains the value in a final field, any {@link this#subscribe(Subscriber)} will
 * replay the value. This is a "Cold" fluxion.
 * <p>
 * Create such fluxion with the provided factory, E.g.:
 * <pre>
 * {@code
 * Streams.just(1).subscribe(
 *    log::info,
 *    log::error,
 *    (-> log.info("complete"))
 * )
 * }
 * </pre>
 * Will log:
 * <pre>
 * {@code
 * 1
 * complete
 * }
 * </pre>
 *
 * @author Stephane Maldini
 */
final class FluxJust<T> extends Flux<T> implements Fuseable.ScalarCallable<T>, Fuseable,
		Receiver {

	final T value;

	FluxJust(T value) {
		this.value = Objects.requireNonNull(value, "value");
	}

	@Override
	public T call() {
		return value;
	}

	@Override
	public void subscribe(final Subscriber<? super T> subscriber) {
		try {
			subscriber.onSubscribe(new WeakScalarSubscription<>(value, subscriber));
		}
		catch (Throwable throwable) {
			subscriber.onError(Operators.onOperatorError(throwable));
		}
	}

	@Override
	public Object upstream() {
		return value;
	}

	static final class WeakScalarSubscription<T> implements QueueSubscription<T>,
	                                                        Receiver, Trackable {

		boolean terminado;
		final T                     value;
		final Subscriber<? super T> subscriber;

		WeakScalarSubscription(T value, Subscriber<? super T> subscriber) {
			this.value = value;
			this.subscriber = subscriber;
		}

		@Override
		public void request(long elements) {
			if (terminado) {
				return;
			}

			terminado = true;
			if (value != null) {
				subscriber.onNext(value);
			}
			subscriber.onComplete();
		}

		@Override
		public void cancel() {
			terminado = true;
		}

		@Override
		public boolean isStarted() {
			return !terminado;
		}

		@Override
		public boolean isTerminated() {
			return terminado;
		}

		@Override
		public Object upstream() {
			return value;
		}


		@Override
		public int requestFusion(int requestedMode) {
			if ((requestedMode & Fuseable.SYNC) != 0) {
				return Fuseable.SYNC;
			}
			return 0;
		}

		@Override
		public T poll() {
			if (!terminado) {
				terminado = true;
				return value;
			}
			return null;
		}

		@Override
		public boolean isEmpty() {
			return terminado;
		}

		@Override
		public int size() {
			return isEmpty() ? 0 : 1;
		}

		@Override
		public void clear() {
			terminado = true;
		}
	}
}
