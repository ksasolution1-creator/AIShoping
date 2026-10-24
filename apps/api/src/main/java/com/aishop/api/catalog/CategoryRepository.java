package com.aishop.api.catalog;

import static com.aishop.api.jooq.Tables.CATEGORIES;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;

import java.util.List;

import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.CatalogViews.Category;

@Repository
public class CategoryRepository {

	private final DSLContext dsl;

	public CategoryRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public List<Category> findActive() {
		return dsl.select(CATEGORIES.ID, CATEGORIES.PARENT_ID, CATEGORIES.NAME, CATEGORIES.SORT_ORDER)
			.from(CATEGORIES)
			.where(CATEGORIES.IS_ACTIVE.isTrue())
			.orderBy(CATEGORIES.PARENT_ID.nullsFirst(), CATEGORIES.SORT_ORDER, CATEGORIES.ID)
			.fetch(r -> new Category(r.value1(), r.value2(), r.value3(), r.value4()));
	}

	public boolean exists(long id) {
		return dsl.fetchExists(CATEGORIES, CATEGORIES.ID.eq(id).and(CATEGORIES.IS_ACTIVE.isTrue()));
	}

	public long insert(String name, Long parentId, int sortOrder) {
		return dsl.insertInto(CATEGORIES)
			.set(CATEGORIES.NAME, name)
			.set(CATEGORIES.PARENT_ID, parentId)
			.set(CATEGORIES.SORT_ORDER, sortOrder)
			.returning(CATEGORIES.ID)
			.fetchOne(CATEGORIES.ID);
	}

	/** 자기 자신과 모든 하위 카테고리 ID (재귀 CTE) */
	public List<Long> findSelfAndDescendantIds(long categoryId) {
		Field<Long> treeId = field(name("tree", "id"), Long.class);
		CommonTableExpression<Record1<Long>> tree = name("tree").fields("id").as(
				select(CATEGORIES.ID).from(CATEGORIES).where(CATEGORIES.ID.eq(categoryId))
					.unionAll(select(CATEGORIES.ID)
						.from(CATEGORIES)
						.join(table(name("tree"))).on(CATEGORIES.PARENT_ID.eq(treeId))));
		return dsl.withRecursive(tree).select(treeId).from(tree).fetch(treeId);
	}

}
