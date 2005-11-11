/*
 * Copyright 1999-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */
 
package org.apache.fop.layoutmgr;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.apache.fop.area.RegionReference;
import org.apache.fop.area.Area;
import org.apache.fop.area.Block;
import org.apache.fop.fo.pagination.PageSequence;
import org.apache.fop.fo.pagination.SideRegion;
import org.apache.fop.fo.pagination.StaticContent;
import org.apache.fop.layoutmgr.inline.InlineLevelLayoutManager;
import org.apache.fop.traits.MinOptMax;

/**
 * LayoutManager for an fo:flow object.
 * Its parent LM is the PageSequenceLayoutManager.
 * This LM is responsible for getting columns of the appropriate size
 * and filling them with block-level areas generated by its children.
 */
public class StaticContentLayoutManager extends BlockStackingLayoutManager {

    private RegionReference targetRegion;
    private Block targetBlock;
    private SideRegion regionFO;

    private int contentAreaIPD = 0;
    private int contentAreaBPD = -1;

    /**
     * Creates a new StaticContentLayoutManager.
     * @param pslm PageSequenceLayoutManager this layout manager belongs to
     * @param node static-content FO
     * @param reg side region to layout into
     */
    public StaticContentLayoutManager(PageSequenceLayoutManager pslm, 
            StaticContent node, SideRegion reg) {
        super(node);
        setParent(pslm);
        regionFO = reg;
        targetRegion = getCurrentPV().getRegionReference(regionFO.getNameId());
    }

    /**
     * Creates a new StaticContentLayoutManager.
     * @param pslm PageSequenceLayoutManager this layout manager belongs to
     * @param node static-content FO
     * @param block the block to layout into
     */
    public StaticContentLayoutManager(PageSequenceLayoutManager pslm, 
            StaticContent node, Block block) {
        super(node);
        setParent(pslm);
        targetBlock = block; 
    }

    /** @see org.apache.fop.layoutmgr.LayoutManager */
    public LinkedList getNextKnuthElements(LayoutContext context, int alignment) {
        if (true) {
            throw new UnsupportedOperationException(
                "Shouldn't this method be emptied because it's never called at all?");
        }
        //TODO Empty this method?!?
        // set layout dimensions
        setContentAreaIPD(context.getRefIPD());
        setContentAreaBPD(context.getStackLimit().opt);

        //TODO Copied from elsewhere. May be worthwhile to factor out the common parts. 
        // currently active LM
        BlockLevelLayoutManager curLM;
        BlockLevelLayoutManager prevLM = null;
        MinOptMax stackSize = new MinOptMax();
        LinkedList returnedList;
        LinkedList returnList = new LinkedList();

        while ((curLM = ((BlockLevelLayoutManager) getChildLM())) != null) {
            if (curLM instanceof InlineLevelLayoutManager) {
                log.error("inline area not allowed under flow - ignoring");
                curLM.setFinished(true);
                continue;
            }

            // Set up a LayoutContext
            MinOptMax bpd = context.getStackLimit();

            LayoutContext childLC = new LayoutContext(0);
            childLC.setStackLimit(MinOptMax.subtract(bpd, stackSize));
            childLC.setRefIPD(context.getRefIPD());

            // get elements from curLM
            returnedList = curLM.getNextKnuthElements(childLC, alignment);
            //System.out.println("FLM.getNextKnuthElements> returnedList.size() = " 
            //    + returnedList.size());

            // "wrap" the Position inside each element
            LinkedList tempList = returnedList;
            KnuthElement tempElement;
            returnedList = new LinkedList();
            ListIterator listIter = tempList.listIterator();
            while (listIter.hasNext()) {
                tempElement = (KnuthElement)listIter.next();
                tempElement.setPosition(new NonLeafPosition(this, tempElement.getPosition()));
                returnedList.add(tempElement);
            }

            if (returnedList.size() == 1
                && ((KnuthElement)returnedList.getFirst()).isPenalty()
                && ((KnuthPenalty)returnedList.getFirst()).getP() == -KnuthElement.INFINITE) {
                // a descendant of this flow has break-before
                returnList.addAll(returnedList);
                return returnList;
            } else {
                if (returnList.size() > 0) {
                    // there is a block before this one
                    if (prevLM.mustKeepWithNext()
                        || curLM.mustKeepWithPrevious()) {
                        // add an infinite penalty to forbid a break between blocks
                        returnList.add(new KnuthPenalty(0, 
                                KnuthElement.INFINITE, false, 
                                new Position(this), false));
                    } else if (!((KnuthElement) returnList.getLast()).isGlue()) {
                        // add a null penalty to allow a break between blocks
                        returnList.add(new KnuthPenalty(0, 0, false, new Position(this), false));
                    }
                }
/*LF*/          if (returnedList.size() > 0) { // controllare!
                    returnList.addAll(returnedList);
                    if (((KnuthElement)returnedList.getLast()).isPenalty()
                            && ((KnuthPenalty)returnedList.getLast()).getP() 
                                    == -KnuthElement.INFINITE) {
                        // a descendant of this flow has break-after
/*LF*/                  //System.out.println("FLM - break after!!");
                        return returnList;
                    }
/*LF*/          }
            }
            prevLM = curLM;
        }

        setFinished(true);

        if (returnList.size() > 0) {
            return returnList;
        } else {
            return null;
        }
    }
    
    /**
     * @see org.apache.fop.layoutmgr.LayoutManager#addAreas(PositionIterator, LayoutContext)
     */
    public void addAreas(PositionIterator parentIter, LayoutContext layoutContext) {
        AreaAdditionUtil.addAreas(this, parentIter, layoutContext);

        flush();
        targetRegion = null;
    }


    /**
     * Add child area to a the correct container, depending on its
     * area class. A Flow can fill at most one area container of any class
     * at any one time. The actual work is done by BlockStackingLM.
     * @see org.apache.fop.layoutmgr.LayoutManager#addChildArea(Area)
     */
    public void addChildArea(Area childArea) {
        if (getStaticContentFO().getFlowName().equals("xsl-footnote-separator")) {
            targetBlock.addBlock((Block)childArea);
        } else {
            targetRegion.addBlock((Block)childArea);
        }
    }

    /**
     * @see org.apache.fop.layoutmgr.LayoutManager#getParentArea(Area)
     */
    public Area getParentArea(Area childArea) {
        if (getStaticContentFO().getFlowName().equals("xsl-footnote-separator")) {
            return targetBlock;
        } else {
            return targetRegion;
        }
    }

    /**
     * Does the layout for a side region. Called by PageSequenceLayoutManager.
     */
    public void doLayout() {
        int targetIPD = 0;
        int targetBPD = 0;
        int targetAlign = EN_AUTO;
        StaticContentBreaker breaker;

        if (getStaticContentFO().getFlowName().equals("xsl-footnote-separator")) {
            targetIPD = targetBlock.getIPD();
            targetBPD = targetBlock.getBPD();
            targetAlign = EN_BEFORE;
        } else {
            targetIPD = targetRegion.getIPD();
            targetBPD = targetRegion.getBPD();
            targetAlign = regionFO.getDisplayAlign();
        }
        setContentAreaIPD(targetIPD);
        setContentAreaBPD(targetBPD);
        breaker = new StaticContentBreaker(this, targetIPD, targetAlign);
        breaker.doLayout(targetBPD);
        if (breaker.isOverflow()) {
            if (!getStaticContentFO().getFlowName().equals("xsl-footnote-separator")
                && regionFO.getOverflow() == EN_ERROR_IF_OVERFLOW) {
                //TODO throw layout exception
            }
            log.warn("static-content overflows the available area.");
        }
    }
    
    /**
     * Convenience method that returns the Static Content node.
     * @return the static content node
     */
    protected StaticContent getStaticContentFO() {
        return (StaticContent) fobj;
    }

    private class StaticContentBreaker extends AbstractBreaker {       
        private StaticContentLayoutManager lm;
        private int displayAlign;
        private int ipd;
        private boolean overflow = false;
        
        public StaticContentBreaker(StaticContentLayoutManager lm, int ipd, 
                int displayAlign) {
            this.lm = lm;
            this.ipd = ipd;
            this.displayAlign = displayAlign;
        }

        /** @see org.apache.fop.layoutmgr.AbstractBreaker#observeElementList(java.util.List) */
        protected void observeElementList(List elementList) {
            String elementListID = getStaticContentFO().getFlowName();
            String pageSequenceID = ((PageSequence)lm.getParent().getFObj()).getId();
            if (pageSequenceID != null && pageSequenceID.length() > 0) {
                elementListID += "-" + pageSequenceID;
            }
            ElementListObserver.observe(elementList, "static-content", elementListID);
        }
        
        /** @see org.apache.fop.layoutmgr.AbstractBreaker#isPartOverflowRecoveryActivated() */
        protected boolean isPartOverflowRecoveryActivated() {
            //For side regions, this must be disabled because of wanted overflow.
            return false;
        }

        public boolean isOverflow() {
            return this.overflow;
        }
        
        protected LayoutManager getTopLevelLM() {
            return lm;
        }

        protected LayoutContext createLayoutContext() {
            LayoutContext lc = super.createLayoutContext();
            lc.setRefIPD(ipd);
            return lc;
        }
        
        protected LinkedList getNextKnuthElements(LayoutContext context, int alignment) {
            LayoutManager curLM; // currently active LM
            LinkedList returnList = new LinkedList();

            while ((curLM = getChildLM()) != null) {
                LayoutContext childLC = new LayoutContext(0);
                childLC.setStackLimit(context.getStackLimit());
                childLC.setRefIPD(context.getRefIPD());
                childLC.setWritingMode(context.getWritingMode());

                LinkedList returnedList = null;
                if (!curLM.isFinished()) {
                    returnedList = curLM.getNextKnuthElements(childLC, alignment);
                }
                if (returnedList != null) {
                    lm.wrapPositionElements(returnedList, returnList);
                }
            }
            SpaceResolver.resolveElementList(returnList);
            setFinished(true);
            return returnList;
        }

        protected int getCurrentDisplayAlign() {
            return displayAlign;
        }
        
        protected boolean hasMoreContent() {
            return !lm.isFinished();
        }
        
        protected void addAreas(PositionIterator posIter, LayoutContext context) {
            AreaAdditionUtil.addAreas(lm, posIter, context);    
        }
        
        protected void doPhase3(PageBreakingAlgorithm alg, int partCount, 
                BlockSequence originalList, BlockSequence effectiveList) {
            //Directly add areas after finding the breaks
            this.addAreas(alg, partCount, originalList, effectiveList);
            if (partCount > 1) {
                overflow = true;
            }
        }
        
        protected void finishPart(PageBreakingAlgorithm alg, PageBreakPosition pbp) {
            //nop for static content
        }
        
        protected LayoutManager getCurrentChildLM() {
            return null; //TODO NYI
        }
    } 
    
    /**
     * Returns the IPD of the content area
     * @return the IPD of the content area
     */
    public int getContentAreaIPD() {
        return contentAreaIPD;
    }
   
    /** @see org.apache.fop.layoutmgr.BlockStackingLayoutManager#setContentAreaIPD(int) */
    protected void setContentAreaIPD(int contentAreaIPD) {
        this.contentAreaIPD = contentAreaIPD;
    }
    
    /**
     * Returns the BPD of the content area
     * @return the BPD of the content area
     */
    public int getContentAreaBPD() {
        return contentAreaBPD;
    }
    
    private void setContentAreaBPD(int contentAreaBPD) {
        this.contentAreaBPD = contentAreaBPD;
    }
    
}

