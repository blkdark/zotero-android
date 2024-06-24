package org.zotero.android.pdfjs.data

import android.graphics.PointF
import android.graphics.RectF
import org.zotero.android.database.objects.AnnotationType
import org.zotero.android.pdf.data.AnnotationEditability
import org.zotero.android.pdf.reader.AnnotationKey
import org.zotero.android.sync.Library
import org.zotero.android.sync.LibraryIdentifier
import org.zotero.android.sync.Tag
import java.util.Date

/*data class PdfjsDocumentAnnotation(
    override val key: String,
    override val type: AnnotationType,
    override val page: Int,
    override val pageLabel: String,
    val rects: List<RectF>,
    val paths: List<List<PointF>>,
    override val lineWidth: Float?,
    val author : String,
    val isAuthor : Boolean,
    override val color: String,
    override val comment: String,
    override val text: String?,
    override val sortIndex: String,
    val dateModified: Date,
    override val pageIndex: Int,
    override val shouldRenderPreview: Boolean,
    override val isZoteroAnnotation: Boolean,
    override val previewId: String,
    override var boundingBox: RectF,
): PdfjsAnnotation {
    override fun isAuthor(currentUserId: Long): Boolean {
        return this.isAuthor
    }

    override val readerKey: AnnotationKey
        get() {
            return AnnotationKey(key = this.key, type = AnnotationKey.Kind.document)
        }
    override val tags: List<Tag>
        get() = emptyList()

    override fun paths(boundingBoxConverter: PdfjsAnnotationBoundingBoxConverter): List<List<PointF>> {
        return this.paths
    }

    override fun rects(boundingBoxConverter: PdfjsAnnotationBoundingBoxConverter): List<RectF> {
        return this.rects
    }

    override fun author(displayName: String, username: String): String {
        return this.author
    }

    override fun editability(currentUserId: Long, library: Library): AnnotationEditability {
        when (library.identifier) {
            is LibraryIdentifier.custom -> return AnnotationEditability.editable
            is LibraryIdentifier.group -> {
                if (!library.metadataEditable) {
                    AnnotationEditability.notEditable
                }
                return if (this.isAuthor) AnnotationEditability.editable else AnnotationEditability.deletable
            }
        }
    }
}*/