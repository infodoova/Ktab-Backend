package com.doova.ktab.features.ingestion.pdf;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Walks a single page's content stream — recursing into nested Form XObjects — to find
 * the largest image actually placed on the page, using the current transformation matrix
 * rather than trusting page/image aspect-ratio matching.
 * <p>
 * Recursing into forms is the critical fix over a naive {@code PDResources.getXObjectNames()}
 * scan: scanner-driver output and Ghostscript-processed PDFs routinely wrap the page bitmap
 * inside a Form XObject, and without recursion a scanned page silently reports "no large
 * image" and gets misclassified as DIGITAL. See docs/ocr_engine_v3.md, Phase 1.1 and 1.2.
 * <p>
 * Not thread-safe and not reusable across pages — construct one instance per page.
 */
class ImageCoverageEngine extends PDFStreamEngine {

    private final double pageArea;
    private final int maxFormDepth;

    /** Ancestor path, not a global "seen" set: prevents cycles while still allowing the
     *  same form to be legitimately reused by two sibling placements on one page. */
    private final Deque<COSBase> formAncestors = new ArrayDeque<>();

    private double maxCoverage = 0.0;
    private long largestContributingPixels = 0;

    ImageCoverageEngine(double pageArea, int maxFormDepth) {
        this.pageArea = pageArea;
        this.maxFormDepth = maxFormDepth;

        // Only the operators needed to track CTM and dispatch "Do" correctly.
        addOperator(new Concatenate(this));
        addOperator(new DrawObject(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetMatrix(this));
    }

    double maxCoverage() {
        return maxCoverage;
    }

    long largestContributingPixels() {
        return largestContributingPixels;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName name) {
            PDXObject xobject;
            try {
                xobject = getResources().getXObject(name);
            } catch (IOException e) {
                // one malformed XObject must not abort classification of the rest of the page
                xobject = null;
            }

            if (xobject instanceof PDImageXObject image) {
                recordImage(image);
            } else if (xobject instanceof PDFormXObject form && shouldRecurse(form)) {
                formAncestors.push(form.getCOSObject());
                try {
                    showForm(form);
                } finally {
                    formAncestors.pop();
                }
            }
        }
        super.processOperator(operator, operands);
    }

    private boolean shouldRecurse(PDFormXObject form) {
        return formAncestors.size() < maxFormDepth && !formAncestors.contains(form.getCOSObject());
    }

    private void recordImage(PDImageXObject image) {
        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        double placedArea = Math.abs((double) ctm.getScalingFactorX() * ctm.getScalingFactorY());
        double coverage = pageArea <= 0 ? 0.0 : placedArea / pageArea;

        if (coverage > maxCoverage) {
            maxCoverage = coverage;
            largestContributingPixels = (long) image.getWidth() * image.getHeight();
        }
    }
}
