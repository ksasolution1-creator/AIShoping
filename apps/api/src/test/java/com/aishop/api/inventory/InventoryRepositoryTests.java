package com.aishop.api.inventory;

import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.SKUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aishop.api.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InventoryRepositoryTests {

	private static final long SELLER_ID = 1L;
	private static final long WAREHOUSE_ID = 1L;

	@Autowired
	DSLContext dsl;

	@Autowired
	InventoryRepository inventoryRepository;

	@Test
	void reserveFailsWhenNotEnoughStock() {
		long skuId = createSkuWithStock(3);

		assertThat(inventoryRepository.reserve(skuId, WAREHOUSE_ID, 2)).isTrue();
		assertThat(inventoryRepository.reserve(skuId, WAREHOUSE_ID, 2)).isFalse();
		assertThat(inventoryRepository.availableQty(skuId, WAREHOUSE_ID)).isEqualTo(1);
	}

	@Test
	void concurrentReservationsNeverOversell() throws Exception {
		long skuId = createSkuWithStock(5);
		AtomicInteger succeeded = new AtomicInteger();

		try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				futures.add(pool.submit(() -> {
					if (inventoryRepository.reserve(skuId, WAREHOUSE_ID, 1)) {
						succeeded.incrementAndGet();
					}
				}));
			}
			for (Future<?> f : futures) {
				f.get();
			}
		}

		assertThat(succeeded.get()).isEqualTo(5);
		assertThat(inventoryRepository.availableQty(skuId, WAREHOUSE_ID)).isZero();
	}

	@Test
	void releaseMoreThanReservedFails() {
		long skuId = createSkuWithStock(2);
		inventoryRepository.reserve(skuId, WAREHOUSE_ID, 1);

		assertThatThrownBy(() -> inventoryRepository.release(skuId, WAREHOUSE_ID, 2))
			.isInstanceOf(IllegalStateException.class);

		inventoryRepository.release(skuId, WAREHOUSE_ID, 1);
		assertThat(inventoryRepository.availableQty(skuId, WAREHOUSE_ID)).isEqualTo(2);
	}

	private long createSkuWithStock(int onHand) {
		long productId = dsl.insertInto(PRODUCTS)
			.set(PRODUCTS.SELLER_ID, SELLER_ID)
			.set(PRODUCTS.NAME, "테스트 상품")
			.returning(PRODUCTS.ID)
			.fetchOne(PRODUCTS.ID);
		long skuId = dsl.insertInto(SKUS)
			.set(SKUS.PRODUCT_ID, productId)
			.set(SKUS.SKU_CODE, "TEST-" + UUID.randomUUID())
			.set(SKUS.CONDITION_GRADE, "RETURN_UNOPENED")
			.set(SKUS.PRICE, 10_000L)
			.returning(SKUS.ID)
			.fetchOne(SKUS.ID);
		dsl.insertInto(INVENTORIES)
			.set(INVENTORIES.SKU_ID, skuId)
			.set(INVENTORIES.LOCATION_ID, WAREHOUSE_ID)
			.set(INVENTORIES.ON_HAND, onHand)
			.execute();
		return skuId;
	}

}
