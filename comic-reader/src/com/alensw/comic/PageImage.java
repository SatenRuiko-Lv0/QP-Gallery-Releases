package com.alensw.comic;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.util.Size;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.DoubleConsumer;

final class PageImage {
    private static final int MAX_PIXELS = 2_000_000;
    private static final int MAX_SIDE = 8192;

    private final Drawable drawable;
    final int bytes;

    private PageImage(Drawable drawable) {
        this.drawable = drawable;
        bytes = drawable instanceof BitmapDrawable
                ? ((BitmapDrawable) drawable).getBitmap().getAllocationByteCount()
                : Math.max(1, drawable.getIntrinsicWidth() * drawable.getIntrinsicHeight() * 8);
    }

    Drawable drawable(Resources resources) {
        if (drawable instanceof BitmapDrawable) {
            return new BitmapDrawable(resources, ((BitmapDrawable) drawable).getBitmap());
        }
        return drawable;
    }

    static PageImage decode(ContentResolver resolver, Resources resources, Uri uri, int width,
            DoubleConsumer onAspect) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return Api28.decode(resolver, uri, width, onAspect);
            }
            return decodeLegacy(resolver, resources, uri, width, MAX_SIDE, onAspect);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    static Bitmap thumbnail(ContentResolver resolver, Resources resources, Uri uri, int edge) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return Api28.thumbnail(resolver, uri, edge);
            }
            PageImage image = decodeLegacy(resolver, resources, uri, edge, edge, aspect -> {});
            return image == null ? null : ((BitmapDrawable) image.drawable).getBitmap();
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static double scale(int width, int height, int displayWidth, int maxSide) {
        double limit = Math.min(1.0, displayWidth / (double) width);
        limit = Math.min(limit, maxSide / (double) Math.max(width, height));
        return Math.min(limit, Math.sqrt(MAX_PIXELS / ((double) width * height)));
    }

    private static final class Api28 {
        static PageImage decode(ContentResolver resolver, Uri uri, int displayWidth,
                DoubleConsumer onAspect)
                throws IOException {
            Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resolver, uri),
                    (decoder, info, source) -> {
                        Size size = info.getSize();
                        int width = size.getWidth();
                        int height = size.getHeight();
                        onAspect.accept(height / (double) width);
                        double factor = scale(width, height, displayWidth, MAX_SIDE);
                        decoder.setTargetSize(Math.max(1, (int) (width * factor)),
                                Math.max(1, (int) (height * factor)));
                    });
            return new PageImage(drawable);
        }

        static Bitmap thumbnail(ContentResolver resolver, Uri uri, int edge) throws IOException {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), (decoder, info, source) -> {
                Size size = info.getSize();
                double factor = scale(size.getWidth(), size.getHeight(), edge, edge);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetSize(Math.max(1, (int) (size.getWidth() * factor)),
                        Math.max(1, (int) (size.getHeight() * factor)));
            });
        }
    }

    private static PageImage decodeLegacy(ContentResolver resolver, Resources resources, Uri uri,
            int displayWidth, int maxSide, DoubleConsumer onAspect) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, options);
        }
        int width = options.outWidth;
        int height = options.outHeight;
        if (width <= 0 || height <= 0) {
            return null;
        }

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        if ("image/jpeg".equals(options.outMimeType)) {
            try (InputStream stream = resolver.openInputStream(uri)) {
                orientation = new ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (IOException | RuntimeException invalidExif) {
                // A JPEG can still be displayed when its metadata is unreadable.
            }
        }
        boolean swap = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
                && orientation <= ExifInterface.ORIENTATION_ROTATE_270;
        onAspect.accept(swap ? width / (double) height : height / (double) width);
        double factor = swap ? scale(height, width, displayWidth, maxSide)
                : scale(width, height, displayWidth, maxSide);
        options.inSampleSize = 1;
        while (1.0 / options.inSampleSize > factor) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        try (InputStream stream = resolver.openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap == null) {
                return null;
            }
            Matrix transform = exifTransform(orientation);
            if (!transform.isIdentity()) {
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), transform, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                    bitmap = rotated;
                }
            }
            return new PageImage(new BitmapDrawable(resources, bitmap));
        }
    }

    private static Matrix exifTransform(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                break;
        }
        return matrix;
    }
}
