/*
 * $Id$
 * Copyright (C) 2001 The Apache Software Foundation. All rights reserved.
 * For details on use and redistribution please refer to the
 * LICENSE file included with these sources.
 */

package org.apache.fop.extensions;

import org.apache.fop.fo.*;
import org.apache.fop.pdf.PDFGoTo;
import org.apache.fop.pdf.PDFAction;
import org.apache.fop.datatypes.IDReferences;
import org.apache.fop.messaging.MessageHandler;

import java.util.*;


public class Outline extends ExtensionObj {
    private Label _label;
    private Vector _outlines = new Vector();

    private String _internalDestination;
    private String _externalDestination;

    /**
     * The parent outline object if it exists
     */
    private Outline _parentOutline;

    /**
     * an opaque renderer context object, e.g. PDFOutline for PDFRenderer
     */
    private Object _rendererObject;


    public static class Maker extends FObj.Maker {
        public FObj make(FObj parent, PropertyList propertyList) {
            return new Outline(parent, propertyList);
        }

    }

    public static FObj.Maker maker() {
        return new Outline.Maker();
    }

    public Outline(FObj parent, PropertyList propertyList) {
        super(parent, propertyList);

        _internalDestination =
            this.properties.get("internal-destination").getString();
        _externalDestination =
            this.properties.get("external-destination").getString();
        if (_externalDestination != null &&!_externalDestination.equals("")) {
            MessageHandler.errorln("WARNING: fox:outline external-destination not supported currently.");
        }

        if (_internalDestination == null || _internalDestination.equals("")) {
            MessageHandler.errorln("WARNING: fox:outline requires an internal-destination.");
        }

        for (FONode node = getParent(); node != null;
                node = node.getParent()) {
            if (node instanceof Outline) {
                _parentOutline = (Outline)node;
                break;
            }
        }

    }

    protected void addChild(FONode obj) {
        if (obj instanceof Label) {
            _label = (Label)obj;
        } else if (obj instanceof Outline) {
            _outlines.addElement(obj);
        }
        super.addChild(obj);
    }


    public void setRendererObject(Object o) {
        _rendererObject = o;
    }

    public Object getRendererObject() {
        return _rendererObject;
    }

    public Outline getParentOutline() {
        return _parentOutline;
    }

    public Label getLabel() {
        return _label == null ? new Label(this, this.properties) : _label;
    }

    public Vector getOutlines() {
        return _outlines;
    }

    public String getInternalDestination() {
        return _internalDestination;
    }



}

