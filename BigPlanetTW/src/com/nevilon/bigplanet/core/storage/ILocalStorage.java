package com.nevilon.bigplanet.core.storage;

import java.io.BufferedInputStream;

import com.nevilon.bigplanet.core.RawTile;

public interface ILocalStorage {

	/**
	 * Очистка файлового кеша
	 */
	public abstract void clear();

	public abstract boolean isExists(RawTile tile);

	/**
	 * Сохраняет тайл в файловый кеш
	 * 
	 * @param tile
	 *            параметры тайла
	 * @param data
	 *            параметры тайла
	 */
	public abstract void put(RawTile tile, byte[] data);

	/**
	 * Возвращает заданный тайл или null(если не найден)
	 * 
	 * @param tile
	 *            параметры тайла
	 * @return тайл
	 */
	public abstract BufferedInputStream get(RawTile tile);

}