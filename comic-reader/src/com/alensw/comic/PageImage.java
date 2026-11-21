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

final class PageImage {
    private static final int MAX_PIXELS = 2_000_000;
    private static final int MAX_SIDE = 8192;

    final Drawable drawable;
    final float aspect;

    PageImage(Drawable drawable, float aspect) {
        this.drawable = drawable;
        this.aspect = aspect;
    }

    static PageImage decode(ContentResolver resolver, Resources resources, Uri uri, int width) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return Api28.decode(resolver, uri, width);
            }
            return decodeLegacy(resolver, resources, uri, width);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static double scale(int width, int height, int displayWidth) {
        double limit = Math.min(1.0, displayWidth / (double) width);
        limit = Math.min(limit, MAX_SIDE / (double) Math.max(width, height));
        return Math.min(limit, Math.sqrt(MAX_PIXELS / ((double) width * height)));
    }

    private static final class Api28 {
        static PageImage decode(ContentResolver resolver, Uri uri, int displayWidth)
                throws IOException {
            float[] aspect = new float[1];
            Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resolver, uri),
                    (decoder, info, source) -> {
                        Size size = info.getSize();
                        int width = size.getWidth();
                        int height = size.getHeight();
                        aspect[0] = height / (float) width;
                        double factor = scale(width, height, displayWidth);
                        decoder.setTargetSize(Math.max(1, (int) (width * factor)),
                                Math.max(1, (int) (height * factor)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
            return new PageImage(drawable, aspect[0]);
        }
    }

    private static PageImage decodeLegacy(ContentResolver resolver, Resources resources, Uri uri,
            int displayWidth) throws IOException {
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
        double factor = swap ? scale(height, width, displayWidth)
                : scale(width, height, displayWidth);
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
            return new PageImage(new BitmapDrawable(resources, bitmap),
                    swap ? width / (float) height : height / (float) width);
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
