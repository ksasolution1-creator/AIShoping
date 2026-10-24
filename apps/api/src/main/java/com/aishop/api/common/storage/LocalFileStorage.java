package com.aishop.api.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.aishop.api.common.web.ApiException;

@Component
public class LocalFileStorage implements FileStorage {

	public static final String URL_PREFIX = "/files/";

	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"image/jpeg", ".jpg",
			"image/png", ".png",
			"image/webp", ".webp",
			"image/gif", ".gif");

	private final Path root;

	public LocalFileStorage(@Value("${aishop.storage.local-dir:uploads}") String rootDir) {
		this.root = Path.of(rootDir).toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	@Override
	public String store(String directory, MultipartFile file) {
		String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
		String extension = ALLOWED_TYPES.get(contentType);
		if (extension == null || file.isEmpty()) {
			throw ApiException.badRequest("INVALID_FILE", "JPG, PNG, WEBP, GIF 이미지만 올릴 수 있습니다.");
		}
		String relative = directory + "/" + UUID.randomUUID() + extension;
		Path target = resolve(relative);
		try (InputStream in = file.getInputStream()) {
			Files.createDirectories(target.getParent());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException ex) {
			throw new IllegalStateException("파일 저장 실패: " + relative, ex);
		}
		return URL_PREFIX + relative;
	}

	@Override
	public void delete(String url) {
		if (url == null || !url.startsWith(URL_PREFIX)) {
			return;
		}
		try {
			Files.deleteIfExists(resolve(url.substring(URL_PREFIX.length())));
		}
		catch (IOException ex) {
			throw new IllegalStateException("파일 삭제 실패: " + url, ex);
		}
	}

	private Path resolve(String relative) {
		Path path = root.resolve(relative).normalize();
		if (!path.startsWith(root)) {
			throw ApiException.badRequest("INVALID_PATH", "잘못된 파일 경로입니다.");
		}
		return path;
	}

}
