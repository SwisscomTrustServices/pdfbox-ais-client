/*
 * Copyright 2021 Swisscom Trust Services (Schweiz) AG
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
package com.swisscom.ais.client.impl;

import com.swisscom.ais.client.RestClientException;
import com.swisscom.ais.client.model.AbstractUserData;
import com.swisscom.ais.client.model.VisibleSignatureDefinition;
import com.swisscom.ais.client.rest.model.DigestAlgorithm;
import com.swisscom.ais.client.rest.model.SignatureType;
import com.swisscom.ais.client.utils.Trace;
import com.swisscom.ais.client.utils.Utils;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.*;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.util.Matrix;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Calendar;
import java.util.List;

import static com.swisscom.ais.client.utils.Utils.closeResource;

public class PdfDocument implements Closeable {

    private final InputStream contentIn;
    private final OutputStream contentOut;
    private final VisibleSignatureDefinition signatureDefinition;
    private final ByteArrayOutputStream inMemoryStream;
    private final String name;
    private final Trace trace;

    private String id;
    private PDDocument pdDocument;
    private ExternalSigningSupport pbSigningSupport;
    private String base64HashToSign;
    private DigestAlgorithm digestAlgorithm;

    // ----------------------------------------------------------------------------------------------------

    public PdfDocument(String name, InputStream contentIn, OutputStream contentOut, VisibleSignatureDefinition signatureDefinition, Trace trace) {
        this.name = name;
        this.contentIn = contentIn;
        this.contentOut = contentOut;
        this.signatureDefinition = signatureDefinition;

        this.inMemoryStream = new ByteArrayOutputStream();
        this.trace = trace;
    }

    public void prepareForSigning(DigestAlgorithm digestAlgorithm,
                                  SignatureType signatureType,
                                  AbstractUserData userData) throws IOException, NoSuchAlgorithmException {
        this.digestAlgorithm = digestAlgorithm;
        id = Utils.generateDocumentId();
        pdDocument = PDDocument.load(contentIn);

        int accessPermissions = getDocumentPermissions();
        if (accessPermissions == 1) {
            throw new RestClientException("Cannot sign document [" + name + "]. Document contains a certification " +
                    "that does not allow any changes.");
        }

        PDSignature pdSignature = new PDSignature();
        Calendar signDate = Calendar.getInstance();

        if (signatureType == SignatureType.TIMESTAMP) {
            // Now, according to ETSI TS 102 778-4, annex A.2, the type of a Dictionary that holds document timestamp should be DocTimeStamp
            // However, adding this (as of Feb/17/2021), it trips the ETSI Conformance Checked online tool, making it say
            // "There is no signature dictionary in the document". So, for now (Feb/17/2021) this has been removed. This makes the
            // ETSI Conformance Checker happy.
            // pdSignature.setType(COSName.DOC_TIME_STAMP);
            pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            pdSignature.setSubFilter(COSName.getPDFName("ETSI.RFC3161"));
        } else {
            pdSignature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            pdSignature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            // Add 3 Minutes to move signing time within the OnDemand Certificate Validity
            // This is only relevant in case the signature does not include a timestamp
            // See section 5.8.5.1 of the Reference Guide
            signDate.add(Calendar.MINUTE, 3);
        }

        pdSignature.setSignDate(signDate);
        pdSignature.setName(userData.getSignatureName());
        pdSignature.setReason(userData.getSignatureReason());
        pdSignature.setLocation(userData.getSignatureLocation());
        pdSignature.setContactInfo(userData.getSignatureContactInfo());

        SignatureOptions options = new SignatureOptions();
        options.setPreferredSignatureSize(signatureType.getEstimatedSignatureSizeInBytes());

        // create a visible signature at the specified coordinates
        if (signatureDefinition != null) {
            Rectangle2D
                    humanRect =
                    new Rectangle2D.Float(signatureDefinition.getX(), signatureDefinition.getY(), signatureDefinition.getWidth(),
                            signatureDefinition.getHeight());
            PDRectangle rect = createSignatureRectangle(pdDocument, humanRect);
            options.setVisualSignature(
                    createVisualSignatureTemplate(pdDocument, signatureDefinition.getPage(), signatureDefinition.getIconPath(), rect, pdSignature));
            options.setPage(signatureDefinition.getPage());
        }

        pdDocument.addSignature(pdSignature, options);
        // Set this signature's access permissions level to 0, to ensure we just sign the PDF, not certify it
        // for more details: https://wwwimages2.adobe.com/content/dam/acom/en/devnet/pdf/pdfs/PDF32000_2008.pdf see section 12.7.4.5
        setPermissionsForSignatureOnly();

        pbSigningSupport = pdDocument.saveIncrementalForExternalSigning(inMemoryStream);

        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm.getDigestAlgorithm());
        byte[] contentToSign = IOUtils.toByteArray(pbSigningSupport.getContent());
        byte[] hashToSign = digest.digest(contentToSign);
        options.close();
        base64HashToSign = Base64.getEncoder().encodeToString(hashToSign);
    }

    public void finishSignature(byte[] signatureContent, List<byte[]> crlEntries, List<byte[]> ocspEntries) {
        try {
            pbSigningSupport.setSignature(signatureContent);
            closeResource(pdDocument, trace);
            closeResource(contentIn, trace);
            closeResource(inMemoryStream, trace);

            byte[] documentBytes = inMemoryStream.toByteArray();

            if (crlEntries != null || ocspEntries != null) {
                pdDocument = PDDocument.load(documentBytes);

                CrlOcspExtender metadata = new CrlOcspExtender(pdDocument, documentBytes, trace);
                metadata.extendPdfWithCrlAndOcsp(crlEntries, ocspEntries);

                pdDocument.saveIncremental(contentOut);
                closeResource(pdDocument, trace);
            } else {
                contentOut.write(inMemoryStream.toByteArray());
            }
            closeResource(contentOut, trace);
        } catch (Exception e) {
            throw new RestClientException("Failed to embed the signature(s) in the document(s) and close the streams - " + trace.getId(), e);
        }
    }

    @Override
    public void close() {
        closeResource(pdDocument, trace);
        closeResource(contentIn, trace);
        closeResource(inMemoryStream, trace);
        closeResource(contentOut, trace);
    }

    // ----------------------------------------------------------------------------------------------------

    /**
     * Get the permissions for this document from the DocMDP transform parameters dictionary.
     *
     * @return the permission integer value. 0 means no DocMDP transform parameters dictionary exists. Other
     * returned values are 1, 2 or 3. 2 is also returned if the DocMDP dictionary is found but did not
     * contain a /P entry, or if the value is outside the valid range.
     */
    private int getDocumentPermissions() {
        COSBase base = pdDocument.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.PERMS);
        if (base instanceof COSDictionary) {
            COSDictionary permsDict = (COSDictionary) base;
            base = permsDict.getDictionaryObject(COSName.DOCMDP);
            if (base instanceof COSDictionary) {
                COSDictionary signatureDict = (COSDictionary) base;
                base = signatureDict.getDictionaryObject("Reference");
                if (base instanceof COSArray) {
                    COSArray refArray = (COSArray) base;
                    for (int i = 0; i < refArray.size(); ++i) {
                        base = refArray.getObject(i);
                        if (base instanceof COSDictionary) {
                            COSDictionary sigRefDict = (COSDictionary) base;
                            if (COSName.DOCMDP.equals(sigRefDict.getDictionaryObject("TransformMethod"))) {
                                base = sigRefDict.getDictionaryObject("TransformParams");
                                if (base instanceof COSDictionary) {
                                    COSDictionary transformDict = (COSDictionary) base;
                                    int accessPermissions = transformDict.getInt(COSName.P, 2);
                                    if (accessPermissions < 1 || accessPermissions > 3) {
                                        accessPermissions = 2;
                                    }
                                    return accessPermissions;
                                }
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }

    private void setPermissionsForSignatureOnly() throws IOException {
        List<PDSignatureField> signatureFields = pdDocument.getSignatureFields();
        PDSignatureField pdSignatureField = signatureFields.get(signatureFields.size() - 1);

        PDSeedValue pdSeedValue = pdSignatureField.getSeedValue();
        if (pdSeedValue == null) {
            COSDictionary newSeedValueDict = new COSDictionary();
            newSeedValueDict.setNeedToBeUpdated(true);
            pdSeedValue = new PDSeedValue(newSeedValueDict);
            pdSignatureField.setSeedValue(pdSeedValue);
        }

        PDSeedValueMDP pdSeedValueMDP = pdSeedValue.getMDP();
        if (pdSeedValueMDP == null) {
            COSDictionary newMDPDict = new COSDictionary();
            newMDPDict.setNeedToBeUpdated(true);
            pdSeedValueMDP = new PDSeedValueMDP(newMDPDict);
            pdSeedValue.setMPD(pdSeedValueMDP);
        }

        pdSeedValueMDP.setP(0); // identify this signature as an author signature, not document certification
    }

    // ----------------------------------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBase64HashToSign() {
        return base64HashToSign;
    }

    public DigestAlgorithm getDigestAlgorithm() {
        return digestAlgorithm;
    }

    // ----------------------------------------------------------------------------------------------------

    private PDRectangle createSignatureRectangle(PDDocument doc, Rectangle2D humanRect) {
        float x = (float) humanRect.getX();
        float y = (float) humanRect.getY();
        float width = (float) humanRect.getWidth();
        float height = (float) humanRect.getHeight();
        PDPage page = doc.getPage(0);
        PDRectangle pageRect = page.getCropBox();
        PDRectangle rect = new PDRectangle();
        // signing should be at the same position regardless of page rotation.
        switch (page.getRotation()) {
            case 90:
                rect.setLowerLeftY(x);
                rect.setUpperRightY(x + width);
                rect.setLowerLeftX(y);
                rect.setUpperRightX(y + height);
                break;
            case 180:
                rect.setUpperRightX(pageRect.getWidth() - x);
                rect.setLowerLeftX(pageRect.getWidth() - x - width);
                rect.setLowerLeftY(y);
                rect.setUpperRightY(y + height);
                break;
            case 270:
                rect.setLowerLeftY(pageRect.getHeight() - x - width);
                rect.setUpperRightY(pageRect.getHeight() - x);
                rect.setLowerLeftX(pageRect.getWidth() - y - height);
                rect.setUpperRightX(pageRect.getWidth() - y);
                break;
            case 0:
            default:
                rect.setLowerLeftX(x);
                rect.setUpperRightX(x + width);
                rect.setLowerLeftY(pageRect.getHeight() - y - height);
                rect.setUpperRightY(pageRect.getHeight() - y);
                break;
        }
        return rect;
    }

    // create a template PDF document with empty signature and return it as a stream.
    private InputStream createVisualSignatureTemplate(PDDocument srcDoc, int pageNum, String iconPath, PDRectangle rect, PDSignature signature)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(srcDoc.getPage(pageNum).getMediaBox());
            doc.addPage(page);
            PDAcroForm acroForm = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(acroForm);
            PDSignatureField signatureField = new PDSignatureField(acroForm);
            PDAnnotationWidget widget = signatureField.getWidgets().get(0);
            List<PDField> acroFormFields = acroForm.getFields();
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);
            acroForm.getCOSObject().setDirect(true);
            acroFormFields.add(signatureField);

            widget.setRectangle(rect);

            // from PDVisualSigBuilder.createHolderForm()
            PDStream stream = new PDStream(doc);
            PDFormXObject form = new PDFormXObject(stream);
            PDResources res = new PDResources();
            form.setResources(res);
            form.setFormType(1);
            PDRectangle bbox = new PDRectangle(rect.getWidth(), rect.getHeight());
            float height = bbox.getHeight();
            Matrix initialScale = null;
            switch (srcDoc.getPage(pageNum).getRotation()) {
                case 90:
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(1));
                    initialScale = Matrix.getScaleInstance(bbox.getWidth() / bbox.getHeight(),
                            bbox.getHeight() / bbox.getWidth());
                    height = bbox.getWidth();
                    break;
                case 180:
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(2));
                    break;
                case 270:
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(3));
                    initialScale = Matrix.getScaleInstance(bbox.getWidth() / bbox.getHeight(),
                            bbox.getHeight() / bbox.getWidth());
                    height = bbox.getWidth();
                    break;
                case 0:
                default:
                    break;
            }
            form.setBBox(bbox);
            PDFont font = PDType1Font.HELVETICA_BOLD;

            // from PDVisualSigBuilder.createAppearanceDictionary()
            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.getCOSObject().setDirect(true);
            PDAppearanceStream appearanceStream = new PDAppearanceStream(form.getCOSObject());
            appearance.setNormalAppearance(appearanceStream);
            widget.setAppearance(appearance);

            try (PDPageContentStream cs = new PDPageContentStream(doc, appearanceStream)) {
                // for 90° and 270° scale ratio of width / height
                // not really sure about this
                // why does scale have no effect when done in the form matrix???
                if (initialScale != null) {
                    cs.transform(initialScale);
                }

                if (iconPath != null) {
                    File image = new File(iconPath);
                    if (image != null && image.exists()) {
                        // show background image
                        // save and restore graphics if the image is too large and needs to be scaled
                        cs.saveGraphicsState();
                        cs.transform(Matrix.getScaleInstance(0.25f, 0.25f));
                        PDImageXObject img = PDImageXObject.createFromFileByExtension(image, doc);
                        cs.drawImage(img, 0, 0);
                        cs.restoreGraphicsState();
                    }
                }

                // show text
                float fontSize = 8;
                float leading = fontSize * 1.5f;
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.setNonStrokingColor(Color.black);
                cs.newLineAtOffset(0, height - leading);
                cs.setLeading(leading);

                Calendar cal = signature.getSignDate();
                ZoneId zoneId = ZoneId.of("Europe/Berlin");
                LocalDateTime localDateTime = LocalDateTime.ofInstant(cal.toInstant(), zoneId);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss");

                String formattedDate = localDateTime.format(formatter);
                String reason = signature.getReason();

                cs.showText(String.format("%s %s", reason, formattedDate));

                cs.endText();
            }

            // no need to set annotations and /P entry
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);

            return new ByteArrayInputStream(baos.toByteArray());
        }
    }


}
