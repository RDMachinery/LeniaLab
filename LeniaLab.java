/*
 * LeniaLab.java
 *
 * A single-file Java Swing laboratory for Lenia, Bert Wang-Chak Chan's
 * continuous generalisation of Conway's Game of Life. Cells hold values
 * between 0 and 1, each cell senses a soft ring-shaped neighbourhood, and
 * time flows in small steps:
 *
 *     A(t + dt) = clip( A(t) + dt * G( K * A(t) ), 0, 1 ),   dt = 1/T
 *
 *   K   a ring-shaped kernel of radius R (applied by FFT convolution)
 *   G   a bell-shaped growth function centred on mu with width sigma
 *
 * With the right parameters the world grows "creatures": smooth, self-
 * organising patterns that hold their shape, glide, rotate and even heal.
 *
 * The 20 species in the gallery come from Bert Chan's catalogue
 * (github.com/Chakazul/Lenia, MIT licence, Copyright (c) 2018 Bert Chan),
 * and the rules follow his reference implementation. Every species here was
 * checked to survive in this implementation.
 *
 * Run:   java LeniaLab.java        (Java 17 or newer)
 *
 * Architect: Mario Gianota
 * Implementation: Claude (Anthropic)
 * Licence: MIT
 */

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

public class LeniaLab extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new LeniaLab().setVisible(true);
        });
    }

    // =====================================================================
    //  Species
    // =====================================================================

    record Species(String code, String name, String family, double R, double T, String b,
                   double m, double s, int kn, int gn, String blurb, String cells) {
        @Override public String toString() { return name + "   (" + family.replaceFirst("^\\S+\\s", "") + ")"; }
        Params params() {
            Params p = new Params();
            p.R = R; p.T = T; p.b = parseB(b); p.m = m; p.s = s; p.kn = kn; p.gn = gn;
            return p;
        }
        double[][] pattern() { return decodeRLE(cells); }
    }

    /** From Bert Chan's catalogue (animals.json), MIT licence, Copyright (c) 2018 Bert Chan. */
    static final Species[] GALLERY = {
        new Species("O2u", "Orbium unicaudatus", "O Orbidae", 13, 10, "1", 0.15, 0.015, 1, 1,
                "The classic Lenia glider: a smooth crescent that swims steadily across the world in a straight line.",
                "7.MD6.qL$6.pKqEqFURpApBRAqQ$5.VqTrSsBrOpXpWpTpWpUpCrQ$4.CQrQsTsWsApITNPpGqGvL$3.IpIpWrOsGsBqXpJ4.LsFrL$A.DpKpSpJpDqOqUqSqE5.ExD$qL.pBpTT2.qCrGrVrWqM5.sTpP$.pGpWpD3.qUsMtItQtJ6.tL$.uFqGH3.pXtOuR2vFsK5.sM$.tUqL4.GuNwAwVxBwNpC4.qXpA$2.uH5.vBxGyEyMyHtW4.qIpL$2.wV5.tIyG3yOxQqW2.FqHpJ$2.tUS4.rM2yOyJyOyHtVpPMpFqNV$2.HsR4.pUxAyOxLxDxEuVrMqBqGqKJ$3.sLpE3.pEuNxHwRwGvUuLsHrCqTpR$3.TrMS2.pFsLvDvPvEuPtNsGrGqIP$4.pRqRpNpFpTrNtGtVtStGsMrNqNpF$5.pMqKqLqRrIsCsLsIrTrFqJpHE$6.RpSqJqPqVqWqRqKpRXE$8.OpBpIpJpFTK!"),
        new Species("O2b", "Orbium bicaudatus", "O Orbidae", 13, 10, "1", 0.15, 0.014, 1, 1,
                "A two-tailed cousin of Orbium that glides just as steadily.",
                "13.pK$14.qV$6.VpA.MpEpKpITqV$4.BpPpNrIrEqDpWpOpLpUqNvT$4.IqRrNsPsKqHJ3.GqOuC$4.TrLsTrPrLpS6.uUD$3.SpWqNrBqLpRqPqE6.vA$2.FpTpMLpHqPqHrVsPrS5.qUqA$K.pCpRG.ErFsRsVuSuPqN4.CrR$pA.pTU3.rWuBuRvXwTwKpF4.rCH$.tPqHH3.qFvAwUwVyJyKwNL2.DqLR$.pGsGA4.vPxSyDxE2yOuHS.XqJT$2.xIE4.sCyHyOvLvRyFxCsGpVpXqGP$2.VsU4.DxQyOvVuSwDwQuBrMqSqCF$3.vG5.tEyKwVvIvKvMtVrXqTpM$4.sU4.qFvDwMvNuUuDsUrKqDO$4.qCrDJ2.pPsKuGuHtOsQrNqKpC$5.pTqTpVpNqFrJsGsKrVrDqFpFD$6.QqCqJqPqVqXqRqHpOTC$8.LWpFpEXPG!"),
        new Species("OG2g", "Gyrorbium gyrans", "O Orbidae", 13, 10, "1", 0.156, 0.0224, 1, 1,
                "A relative of Orbium whose name means 'gyrating'. Compare its path with the straight-swimming Orbium.",
                "10.EL2QLE$7.TpU2qHqCpXpUpNpFL$4.JrVtTuKuPuKtLrXqTqHqCpPpDG$3.qWtDqRpKqEsMuXvBtGrApXpUpSpIO$2.rQrN4.pAuAvRtTrIpUpIpKpFO$.pSsM6.tJwFuNsPsFrVpPpDL$.uFB6.tJ2yO2yLyOyDsKL$pDuC6.pFxW3yOwIwD2xPqH$rNtV5.EsMxCyIyOwXtJsMtJwFuX$sHuSV3.EpDvOwFxEwQsRqR2qHsFvWE$rQvJsWpPQpKpSqCvEvBuCpD3.BpDtGrQ$pXuKvMuPtLsWsCrIuCtBrS6.qWrQ$EsKvEwXyBwLtVrVsCrDqH6.pXrG$.qHtVxJyOwQrQpNqJpPV6.qJqE$.JsUxMyOrX10.pFqRJ$2.rQxPwIpI9.pKqJT$2.qJxEuPpKB7.qCpP$2.EvOvMpPO5.TrGqH$3.sCyOqEpIOEBOqHqEsRtG$4.xMsMqJqCpXqJqRpIqOuCtBsF$5.xPrAqTqMpSE.rSsMrLqRqHV.TpS$6.vErDE2.VpPB$7.pIrNqHpKQ!"),
        new Species("O4s", "Synorbium solidus", "O Orbidae", 13, 10, "1", 0.122, 0.0106, 1, 1,
                "Several Orbium-like units fused into one larger glider that travels as a team.",
                "23.OTVM.qF$18.qHK.pCpDpLpIpApJpRXqN$18.qHsGpKqGqMX4.pLsO$18.OtUsLqLqHA5.RwN$19.uEvVrJRO6.LrS$17.KpHrFwGsV.pBpS6.uC$13.AIVpDpIpPpMsXuLqIqCsHqQ6.rU$12.GpBpKpAUNE2.tCuArUtOuApO5.rI$6.pV4.GpBpAE6.rWvGtUtDwBvOG4.pQpG$6.sDrJ2.IpEpA9.vRwOtWvBxVvF4.pEpR$4.AGrFuEsKWpKpH10.uJyHvEtKwSyOtK3.pFpQ$4.DqDqOuLvPpWpMI10.GyOwUsFtFxKxErPWVpTpI$3.QpIqQqAsDwDtSN12.vNyOtWsTuVwNtXqWqBqFR$2.VpKpAUWpAsEtWG12.pNxLwGuCuFuWtVrTqTpUC$.CpKN2.TEpJuCsV13.tIvVuWtXtLsPrNqLV$.OpC3.pDrCpXsPvCsL12.qLtOtTtAsErLqNpIC$VUT4.rVsHsJwEwOX11.pQrOsCrPqVqFpGH$pLpBQ4.sGuRtNvFyOyD10.TpXqOqRqGpMTE$.uCU4.PvHwFuTwDyOvE7.BpApMpRpLpBOF$.qDpQ5.uPyKuSsPwByJrH5.RpIpANA$2.wF5.qUyOwAsGtKwMuXpQ2.LpEpAC$2.qQA5.xFxOtLtCuUvCsEqEpSpRW$3.uE5.tBxBvBtOtUtVsLrCqFV$3.MrG4.qMuRuVtStBsMrNqLpD$4.pXqKE.CpXsLtJsPrTrBqFpDB$5.pHqKpWpSqLrHrOrDqKpPTB$6.DpKpVqFqGqApMXJ$9.EHGC!"),
        new Species("S1s", "Scutium solidus", "S Scutidae", 13, 10, "1", 0.29, 0.045, 1, 1,
                "A shield-shaped creature that drifts along at about half the speed of Orbium.",
                "5.pGQ$6.sUsDqRR$4.VpXrJwKvNtXrW$2.ApVrGrWsIwKyOyDwTuNO$2.qArOrIqIpPpTxH2yOyIvKqG$rVpIrNrJpH4.xP3yOvFqWA$sKvNsKqE5.pI4yOuHqPC$sNxBuMpD5.JuN3yOwXsUpN$sGxFyOT5.pCtIyF3yOuKqUH$rPwXyOxT5.qKtSxS3yOvIrQT$pQwK2yOtWXJXqHsGuWxW3yOvPsApC$.vQ3yOuVsLsGsXuMwOyK3yOvHrVpC$.rKyK3yOxEwBwCwXyE3yOxQuMrJU$.WvC5yOyM5yOwFtJqOL$2.rVwO9yOwWuMsApOC$2.pLtBwK6yOyJwQuTsQqLP$3.qHsWvEwUxTyCxRwWvRuGsNqSpA$3.HqBrUtHuGuPuLtVsXrSqIXB$4.DpDqEqW2rIqWqGpJN$6.ENTUPHA!"),
        new Species("PG1a", "Gyropteron arcus", "P Pterifera", 13, 10, "1", 0.283, 0.0481, 1, 1,
                "A winged glider from the Pterifera family, the largest family in the catalogue.",
                "8.O2pATKA$7.pLqSrMrSrPrCqHpGH$6.pIrOtHuKvAvCuRtUsOqTpB$5.qXtSvWxNyFyL2yOxSwKuLsApMA$3.rJuFvUxBxXyM6yOxSvJsIpJ$2.qOtKvOxGyH9yOxQvErRU$.pCsJvDxGyL2yOwVvBuTvBvNwJxHyCyDwVtXqKE$.qXuEwTxBuBqR2.OpRqUrArPtDvHxBxIvLrXW$pDsRqDrMrIpH4.JpNqLqJqWsUvPxDwGtEpRA$pQ.qHrKqGD5.XqDqRqTsAuTwVwPtUqHE$.GqMrFpN6.XqFrLrRsQvDxDwWuFqPI$.IqNrCpO5.ApMqUsHtNuRwPxTxCuFqPJ$2.qDrGqHJ4.VqNsFuBwIyB2yOxAtUqKI$2.pIrBrGpTF2.UqWtDvKxL4yOwLtEpXF$2.CqGrTrWqXtXvGwVyE6yOyBvKsEpLC$3.pArPyE11yOwLtWrCW$4.sGwOxWyM7yOyBwKuMsGqAJ$4.rDtKvKwRtMwC4yOxDvPuCsKqPpAA$9.GvByOxUwEuRtGrUqMpFE$10.rCtUuItMsEqVpUXE$10.pTrFrHqNpQVIA$10.C2MGB!"),
        new Species("2PG1v", "Gyropteron serratus velox", "P Pterifera", 18, 10, "1/12,1/4,1/2,1", 0.21, 0.032, 1, 1,
                "A fast, saw-edged winged glider, one of the quickest creatures here.",
                "21.BEG2EB$16.BOpFpSqEqM2qOqJqHpXpKpDOG$14.LpNqRrQsKsU3tDsWsRsKsFrSrGqRpXpFOB$12.OqCrQtBtVuIuKuFuAtTtLtGtDsWsRsMsHrVrNrAqEpKOB$10.BpUrXtQuUvEuXuNuCtQtLtGtBsW2sRsPsKsFsCrVrNrIqRpXpAG$9.QrDtOvGvRvJuSuFtTtLtDsW2sRsP3sMsKsFsCrVrQrLrGqRqHpIL$8.pFsHuS2wDvMuPuAtLsWsPsHsFrX2rV3sC2sF2rXrQrLrGrAqOqEpKO$7.pNsWvRwQwDvGuKtQtBsKrSrIqWqRqOqRqTrArGrIrNrQrV2rSrNrIrAqRqJpXpIL$6.pNtGwDxCwIvGuItJsKrLqOpXpNpIpKpNpSpUqEqMqRqWrDrL3rQrIrDqTqMqEpPpDJ$5.pAtBwIxHwNvJuItBrQqHpIQ2JOVpFpNpUqEqMqRqWrArG2rI2rNrGqWqMqEpSpITE$4.BsHwDxJwVvRuNtBrDpKG3.BLVpIpSqEqMqTrDrG4rI3rLrDqOqHpSpKpAL$4.rAvMxMxEwDuUtDqTQ5.BQpFpSqEqOrDrI7rN2rLrIrAqMpUpKpAOE$3.pIuCxExJwLvGtOqWE6.GpDqCrArXsRtG2tJsWsMsCrV2rSrQrNrIrDqRqCpKpAOG$3.sCwIxMwVvWuIrLG7.pKrItJuXwDwXxJxMxEwNvJuCtBsKrXrVrSrQrLrAqJpNVLG$2.pNuPxHxEwIvBsMV7.qHsWvEwIwVxJxWyG4yOxEvOtVsRsFrXrSrNrGqOpSpALG$2.rXwFxHwNvRtVqE7.qWtTvGvTwDwSxCxRyD6yOwFuIsUsCrVrSrLrAqEpFLE$.QuA2wVwDvBsCG6.qRuCuPuXvJvTwFwSxHxUyL6yOwQuIsUsFrVrNrDqMpKLB$.qOvJwVwLvRuAqE6.qCtLtVuFuNuUvJvTwNwXxRyI7yOwFtVsPsCrQrGqOpPOB$.sHwAwNwAvBsRT5.LsCtDtLtQtVuKuUvOwIwXxU9yOuUtGsHrSrIqTpUQ$BtLwDwAvMuKrGL5.qOsMsPsUtBtLuAuPvMwFxCxW9yOxEuAsRrVrIqTqCT$pIuIvWvRvEtQpXJ4.GrSrXsCsHsMsWtOuI2uKvBwAwQwSwXxW6yOvBtBsCrIqTqCV$qEuPvRvJuStBpSJ4.pN3rLrNrXsKtBsUsWtTvEwFwAvGuUvEwNxU4yOwItGsFrIqRpXV$qWuUvJuXuFsMpPL4.qJ3qTrArIrSrQrNsWvOyDyGwVuXtOtGuAvTxH3yOxCtGsFrIqOpUT$rIuUvBuNuArVpSO4.qHqJqHqEqHqOrDqErDuPyG3yOvTsWrSrXtTvTxJ2yOxMtQsFrGqJpPQ$rLuPuSuItOrNpXTB3.2pUpPpKpNpXpKVrDwL5yOsRqOqJrVuKwF2yOxPtTrXrAqEpIL$rLuIuKuAtGrIqEpDJ3.pKpIpDVpApDE.qOxRyOyI3yOtVpIpFqMtGvMyDyOxJtOrQqRpSpDG$rDuCuFtQtBrGqHpNO3.VQJ3E2.VxH5yOrSGLpPsPuXxPyOwVtDrGqHpIQB$qRtTtVtJsRrDqJpUpDJ2.OG7.tD5yOE.BpKsHuPxJyOwDsKqRpUpAJ$pXtJtQtGsPrDqJqHpPTE.L9.tT3yO3.BpSsKuPxMyDuUrDqEpFLB$pAsUtLtBsKrDqEqJqCpKOETB15.GqJsPvBxPwVtDqCpKQE$.sFtGsUsHrGpP2qJpUpIOJL15.pFrDtGvWxHuXrLpKVG$.rGsWsPsCrIpPqCqMqJpUpIVpIE13.TqHrXuFwLvWsRpKVG$.pX2sMrXrIpXpIqEqMqJpXpPpIpKJ10.ETpUrGtBvGvTtOqMQG$.GrSsKrVrGqJOpPqEqMqJqHpXqOpUTE7.GTpPqWsPuNvGtOqTGE$2.qOsCrSrGqOpATpPqE4qJrAqJpNpAJB2.BJVpUrAsRuCuNtDqTB$2.VrNrSrGqRpN.TpNpXqEqMqOqRrLrGqOqJqCpSpPpUqHrAsCtDtQtLsCqH$3.qMrNrGqRpXL.QpIpSqCqHqMqRqWrSrNrLrQrVsCsKsRsWsRrVqTpI$3.LrArGqRqEpF2.GVpFpKpUqEqJqRqW2rQ3rSrQrGqMpN$4.pPqWqRqEpPL3.JQVpDpIpPpU3pXpUpNpFL$5.qCqOqHpSpA6.2EGJ2LGB$6.qCqHpUpFQ$6.BpSpXpKpAJ$8.2pNpDOB$9.pDpFTG$10.LVLB$12.JG!"),
        new Species("H3s", "Helicium solidus", "H Helicidae", 13, 10, "1", 0.35, 0.06, 1, 1,
                "A large, twisted creature that stays almost still while its body shifts and churns.",
                "9.BG2LID$7.GXpSqIqOqMqFpRpEPE$5.DXqGrOsOtEtHtEsUsKrXrJqPpRTC$4.JpPrKtFuQvMvUvWvX2wAvVvMuRtLrUqCO$3.KqBsEuMwJxNyByI6yOyHwKuErUpRH$2.FpXsKvDxO11yOxIuLrUpRI$2.pKsBvCyE12yOwDtCqQWB9.O$.KrBuHxV4yOyM2yGyM4yOyCuRsNrLpUA9.pEqKpHF$.pKsSwO4yOyFwXwGwJxCyC8yOxOuHrPpWpARNLG2.tArSqLpKXB$.qMuI5yOwEuHtRtWuRwBxO9yOxHuMsTrVrKrBqRqKpWtFuFsSrJqOqM$.rJvO4yOxLtJrQrGrLsGtKvAwWyI7yOyGvSuAsSsBrQ2rOrNsEwNuWtJsHrVpA$BrXwK4yOvKqOpDQVpLqMrVtNvNxFyG2yOwMvGuOtQsLrGqEpLpFpIqArBrWyGwWvHuAtJrC$.sCwRyK3yOtLB5.JpNrBsOtS3.BOSMD5.QqKyCyHxAvPuTsXQ$.rSwJxVyL2yOsP25.JvQyOyGxBwEuMqC$.qXvMwVxT2yOtO26.tP2yOyBxHvQrC$.pTuFvPwNxVyOvWX14.pPpB9.tH3yOyCwHrQ$.HsNuFvBwLxXyBrEpLLB.CJXpRqFqEpOUpJvUwBuPsVrDpQL4.pCuN3yOyNwJrU$2.qNsRtIuQwHxSrVrMqVqKqJqTrPsUuKwGyG5yOyDwSuUtArMqKpTpPqDrSwO4yOvVrN$2.JrGrOsQuEvSrPrDrKrPrVsHtBuFwF9yOxQvVuHtCsIsDsRuPyH4yOuVqU$3.pUpWqQrWtJsNpApNpVqEqLqSrNtAvQ9yOyCwRvMuSuQvJxG4yOxOtMpV$4.GRpQqWsD8.pCrDtRwFyByOyHyG3yOyIxQ2xDxT5yOvLrVU$7.LpMP9.ASqJsPvN6yOyMyN5yOwPtEqEB$19.FpLrNuFxH12yOwUtUrAS$19.MqDsKvAxP10yOyDwGtTrGpE$19.GpPrOtSvRxGyFyOyLyDxTxNxKxDwLvCtDqXpEB$20.GpJqSsDtEtUuHuNuRuVuXuUuKtKrWqGUA$22.FUpNqFqUrJrVsFsHsCrJqJpFH$25.FPpBpL2pQpJUG$28.ACB!"),
        new Species("C0la", "Circium lithos apertus", "C Circidae", 15, 10, "1", 0.16, 0.022, 1, 1,
                "A stationary ring, perfectly still once it settles. Not every creature moves.",
                "5.4yOqR$3.8yO$2.3yO4.4yO$.3yO6.3yO$.2yO8.3yO$2yO9.3yO$2yO9.3yO$2yO9.3yO$2yO9.3yO$pW2yO7.4yO$.3yO5.4yO$2.11yO$2.yF9yO$4.6yO!"),
        new Species("3Es", "Echinium solidus", "E Echinidae", 18, 10, "3/4,1,1/2", 0.226, 0.0272, 1, 1,
                "A spiny, urchin-like creature that creeps slowly.",
                "18.EIC$17.qRvEwOuQpN$17.vK3yOsN$17.wT3yOtV$10.UrArKpSG2.4yOxSV$9.UxH3yOtDpLuI5yOuFpLDAJqOpW$10.5yOvQ2yO2yN3yOyHwQwWyGyOwMpR$9.qE8yOrPxH9yOsW$9.pG7yOsF.xW9yOtC$9.uF2yOtSuHyM2yO2.xJ4yOyB2yOyFuA$6.rRrIQ3yO2.tPyIyC2.sKxL2yOwBtR2yOrA$5.wJ5yOvM3.rM4.TsTtN.sM2yO$5.yL5yOvN12.3yOpGE$5.wO5yOxL11.uU3yOwKuApP$5.uK2yOyJwWwV11.S6yOxHpE$5.uG2yO14.rBvRxQyK4yOtE$5.vA2yO16.pXuH4yOtT$3.PIvNyNyOC16.rL3yOxU$2.sCwHwUxN2yOqV16.3yOtK$2.6yOqN14.rJ3yOuR$.rJyC3yOyKxE14.pG4yO$2.vO2yOsS16.tP4yOqV$2.tC2yOU17.xN4yOpV$2.pExMyOwCpR16.uK4yOrI$2.EwX3yOtAF14.4yOyL$2.vN5yOqF11.qPtMxC2yOwFqU$.tC6yO11.qQ3yOyLvC$.5yOwPJ11.wE3yOvO$sE4yOuIpA12.pU3yOsQ$sGxM4yOtOtGuArGE3.CuPrUG2.xV2yOxUpI$qHvDvEuCvNyM4yOuGpC2.yM2yOvQrSrF4yOpH$.L3.vE5yOuKG.yJ8yOyN$5.vG6yOrJpIwV3yOyJwVwDuNuL$5.2yOyExU4yOwKxC3yOvJU$5.2yOpXvC2yOxFyE5yOrS$5.xTyOuC2yOuT.qOxO4yOG$5.sRyB2yOxK3.tK4yO$6.qItMtG4.qBwQxOwV$13.pItMrV!"),
        new Species("3GH2n", "Hydrogeminium natans", "G Geminidae", 18, 10, "1/2,1,2/3", 0.26, 0.036, 1, 1,
                "A 'swimming twin': two linked bodies with a striped interior, travelling together.",
                "37.BEG3JGE$35.JpApPqCqJqOqMqEpUpKpAQG$33.BVqCrLsPtLtVuCtTtJsPrVrDqJpPTB$33.pIrAsRuIvWwXxMxPxHwQvTuStTsUrSqOpFG$32.pKrXuIwFxU6yOyLxJwFvBtVsPrDpNJ$31.TsCvT11yOyIwXvTuNsWrDpKG$31.rIwA14yOxHvWuKsRqTpDB$30.rQwS16yOxHvRuArXpXL$29.tG7yOyL4yOyL6yOwXvBsWqTpDB$28.tT7yOxUwSwIwVxHxJxMxUyI4yOyIwIuArNpNG$27.sW7yOxMvOtQsPtBuIvJwAwQxPyL4yOxPvBsHpXL$26.qTyL4yOxMvMuUtTrXqCpKqErStLuSvRwVyB5yOwFtDqMQ$25.pKuPyD3yOwVpS7.pIrQtJuSwDxPyL4yOxHuCrAV$24.QrXvOyB3yOqH9.pDrLtGvBxCyL4yOyBuSrLpA$23.rItDvMxU3yOtV11.pArGtLwIyI4yOyIvBrQpD$22.BuIwSyI2yOvE14.pNrXvMyG4yOyGvBrQpD$23.tJyD2yOpU15.VrNvEyB4yOxPuKrIpA$22.pItVyL2yO16.pSsRwAyD4yOwQtLqRQ$21.rLvJxW2yOsP16.rLvExPyL4yOvMsKpXG$20.tOxJ3yOwLsMJ15.sWxC5yOyBuCrIpF$20.wDyLyOyGuCsHrLpU15.tQyB5yOwLsPqEO$19.sCwS2yOtTpDLpNqHO14.uCyL5yOuFrApDB$16.JtTwNxCyG2yOtG2.BVG13.qRwF5yOuXrQpIG$16.rL6yOxUpD15.qJvRyB5yOuNrQpF$16.tJ7yOvO15.7yOtVqOV$14.pFrX3yOuXTtT3yOG13.rQ6yOvBpF$12.uI6yOtD2.xEyOxH14.6yOvJQ$9.JqEtB8yO2.JsKqR9.pFqErNuC3yOyLyGyLsU$9.tG4yOyD.rG3yOtO8.qCqE3.qT6yOxEtLB$8.wV5yO3.wS2yOtL7.qRwQuFE2.sU5yOxMtT$7.sCyL4yOwFJ3.xUxR8.vG2yOrS2.2yOuUuXyGxEtQ$4.tTvWuFvT2yO2.vWvMpU10.rA2.sR7yOsWsCvMqO$4.vGwNwLxRyOpI2.pIxWuC9.2yOrN.J7yOqM$4.uPwXxMyIyOV2.EyOuS8.uF2yOwV2.6yOuS$4.uSuUvB2yOqEB.JE9.vG3yOrArV2yOuIuPyOwV$4.sP2vRwXyOyGqJL8.O3.yG6yOrLqMvB$3.pFtT2xM3yOsMJ7.rGtQ3.xH6yO$3.rAvM4yOwIqW8.sFwDtQ2.xE5yOrG$.pPqTsHvOyL2yOwQsKV8.pAtT2uCwDyL4yOuP$rGtBsRsUuPxHyOwSuFqWO9.rIvJyB4yOvJuCpFJ$qJtQtOtBuAuFuUuPtTrQpIGEVvTyOV3.rIxC4yOuAsPrGpPB$.rGsRrGpUqWsCsWtQtGrQqCpPqWvWyOsKB2.uI4yOtJrAV$5.pKrDsFtQvMvTuUtDrQqTsW2uIvOyI4yOuUrL$5.OrLtOvGxWyLyOwVsHpPpNyD7yOvRpI$6.sKxC2yIyLyOxWuPrIuN7yOuUqJ$5.QuNyB2yOyLyIyL7yOyIpK$5.GsUxJ2yOyGwSvWwNxW4yOyLG$6.pKuAxHxUwNuCsRtBuSxCyG2yLsK$8.qJsRsWsCrApXqCsUqWG$10.TqHqMpN$11.QqHpK!"),
        new Species("2GG2g", "Gyrogeminium gyrans", "G Geminidae", 18, 10, "3/4,1", 0.32, 0.05, 1, 1,
                "A rotating twin creature that sweeps across the world.",
                "19.DFIL2MKHEB$15.AFNUpFpMpQpRpPpLpFVOHC$13.CKVpKqAqNqXrFrHrFqWqMqBpNpCQIC$11.AJXpQqKrGrWsMsTsVsTsLsBrLqVqHpTpHVMFB$10.ESpNqLrLsKtHtWuIuMuKuDtQtBsLrVrIqUqIpVpJpAOGB$9.IpCqCrFsJtLuMvGvS2vXvSvIuTuFtQtEsRsGrTrIqUqHpTpHUMFCB$8.LpKqOrUtEuKvPwLxAxIxMxKxCwOwBvOvDuRuHtWtMtCsPsDrOrBqOqGqBpWpV2pUpSpQpOpLpIpEXRKEA$7.NpRrBsKtVvHwOxOyE4yOyKxXxMxCwQwGvVvOvFuVuMuCtStLtHtItLtO2tQtPtLtFsXsRsKsDrSrHqQpXpGOD$6.MpVrLsXuMwBxM10yOyNyBxOxGwXwSwOwK2wHwLwQwWxBxE2xGxExCwXwTwPwLwEvQvBuEtDsAqUpRRC$5.HpWrStLvDwSyG13yOyLyDyAyByEyK18yOyDwUvLtVsHqSpJJ$4.ApPrTtUvQxI42yOxAvFtHrLpTN$4.pBrOuAwBxU5yOyLyN17yOyNyJ2yFyIyL14yOxTvUtSrSpWN$3.DqWtUwHyH4yOyJxXxWyEyL6yOyNyLyJyIyJ2yLyIxQwGvOvWwDwOxCxMxUyByGyL11yOxXvWtRrOpRJ$3.pRtFwEyN4yOyExGwTxBxPyCyJyKyLyKyJyGyCxX3xWxVxNvNtFsAsDsOtGuBuRvGvQwBwKwVxIxSyCyHyL7yOxOvLtFrApEC$2.ArVvPyN4yOyHwS2vNwDwWxMxTxUxTxQxNxKxJxI2xJxHwTuFrApARXpLqFqWrNsAsJsStFtTuLvDvTwJxAxOyDyM5yOwRuMsGqDN$2.pSuIyB5yOwSuNtTuIvFwCwPwSwRwOwKwIwJwOwUwXwWwJtWpX7.GOWpHpTqJrCrXtBuGvMwQxQyI5yOxNvItCqWpCB$2.sAwU5yOxOuLsHsLtHuHvBvKvJvFvAuVuWvGvVwKwQwHuKqB14.KpXrStQvKwVxWyM5yOyCvUtOrKpLE$.QuL6yOvLrRqKrKsItItVuAtTtNtHtGtMuHvJwFwHvEqX16.pUsJuRwPxUyL7yOwEtUrPpQH$.qSwU5yOyAsRpKXqIrIsFsNsLsErUrOrPsFtNvDwCvPsK16.FrJuHwOyB9yOwPuCrRpRI$.sV6yOwCqB2.pHqIrArDqUqLqBpUpWrAtEvGvXuKpF16.qHtNwFyA10yOxJuMrVpRH$.uSyN5yOtQA2.LpJpSpNpERIDKqJtIvSvVsJ16.RsDuWxDyK10yOyJvEsFpSG$pCwJyI5yOrO4.QMB5.qHuFwVvXqS16.qCsUvExEyK11yOvUsNpUF$qKxHyByK3yOyMqB4.E7.qTwByOwHpGDB14.qGsMuOwNyB11yOwJsVpWF$rMwTxOyDyL2yOxDXC.CFB7.sH2yOwXpTpEUPG12.pNrMtLvMxJyK10yOwQtApWD$sIwGxBxQyF2yOvXpIKHNQF7.vC2yOxMrRqXqMqBpKO11.IqGsGuLwQyE10yOwPsVpSC$tBvPwLxDxTyKyOvCqApBWpDpCK6.EyN2yOxMtKsRsFrRrCqEW11.pGrOuGwVyM10yOwDsJpK$tNvAvTwNxIyFyOuMqSpVpRpVpPSA5.rF3yOwOuCtJsRsCrSrKqPpGC4.DLG2.pUsSwAyK11yOvDrOW$tJuJvDvVwRxRyMuArJqRqNqPqEpGJ3.AXuMyOyMyGuTtNsPrQrAqTrBrKrDpVSKTpVrCrMqVqBrNuCxI12yOxDtOqMJ$sStQuLvFwBxDyEtNrVrKrHrFqVqBpENKLpDqTwTyJxMwWsUrXqUpSpCpBpNqNrNrSrGrErXtCtUuDuAvX14yOxPuPrRpGA$sDtAtSuMvJwKxMsXrWrTrSrQrHqSqEpQpOpVqRsCxIxCvTvCqUpXR4.WqMrXsRtDtPtUtXuNvJ10yOyDwJwBwKvQtXrUpSH$rLsItCtTuPvOwQsHrMrPrRrQrJrBqUqQqRqXrLrXwHvItVsQP7.pCrEsRtJtGsMsGtFvByK8yOuEqSqRrFrPrKqNpII$qTrPsJtDtUuRvSrNqRqXrDrCqX3qT2qUqXqWuMtNsAqB9.pUrTsJrKpVpNqTtTxDxXyJ5yOsKpVpBOK2LGA$qEqXrPsItCtVuTqTpQpXqEqDqBpX3qBpXpTpLsLrUqG11.pVqAH3.rLuUwCxAxTyJ2yOrAUE$pKqFqWrNsGtAtUpVOVpCpBXW2XVSLAqGqHP17.ArJsUuCvJwOwRqL$RpLqEqUrKsEsVpB12.AT20.CpIqMrDqK$ASpJqCqPrGrXI$2.PpGpUqJrA$3.LpBpLqB$4.EOpC$6.D!"),
        new Species("3Ct14", "Tetradecactenium", "Ct Ctenidae", 20, 10, "1,1,1", 0.27, 0.0377, 1, 1,
                "A huge comb-like creature with fourteen ridges, travelling as one. Use a 256 or 512 grid.",
                "24.A4.BFJLJGC$20.BHMOLGDGSpJpVqBpUpKXOKIFC$18.BNpHpVqCpUpGUpApVrEsCsKsErLqPpXpRpQpOpJpBPGB$13.AE2DKpJqSrSsErXrDqFqErJtFuNvCuXuEsXrWrNrPrSrPrEqNpSpBNHDA$12.IUVSpCqKsKtV2uLtPsHrTtBvIxAxQxSxDvSuItRtWuFuEtUtDsErCqEpMpDVOHB$11.QpQpXpRpVrKuDwAwRwVwJvAtWuVxM5yOxBwFwKwTwQwGvTvAtTsNrPrCqQqIpUpGPE$10.TqMrEqWqUsLvSyE3yOxXwPwX11yOyBxVxOwPvIuHtOtDsQsDrLqNpOSFA$9.MrDsLsKsEtNxH22yOyExAwFvNvAuMtWtBrWqRpQXMFB$9.rNtVuFtVuV3yOyMyI6yOyKyN6yOyGyE5yOxOwPwBvTvJuLtFsAqXqEpPpERHA$8.rPvHwHwBwO3yOyHxJxSyK3yOyI2xMyI5yOxIwIyE5yOxJwKwNwXwRvSuNtLsOrUrEqLpSpAJA$7.rKwR6yOyK2wHxIyEyLyIxLwAvNwNyA3yOyNwPuIwQyN4yOxGvPvWxJyIxWwTvRuUuEtMsTrVqUpTWHA$6.qXxU7yOvKuPvPwWxPxNwGuHtHuBvTxPyLyOyBvSsSuKxM3yOyCwGtVtWwF3yOxIwEvNvGuXuHtFrVqQpRpAMEA$5.qEyK7yOvCsXtQvEwGwJuXsOrArJtBvExCxUxEuOrCrSvIxXyOyKwWuQrQrHtQxV2yOyHwNvOvNwBwHvRuLtBrUqVqEpOpBND$4.RyE7yOvPrTrRtCuKuUtNqWQMqDsJuRwGvWtHpITsGwAyAxUvLsQpORqRvQ2yOyAvLuDuFvLwXxOwVvNuItIsNrUrCqHpLQD$4.wVyJ6yOxCrUqNrGsJsVrSpA3.pIrWuDuMsCC.UtDwQxIuIqP3.tDyDyJwTtKrRrQtHwGyLyOxOwGvHuQuEtOsSrQqMpKOD$3.uQxJyF6yOtBqUqQ2qWpQ5.UrMsQqT3.pOuMxAuFT3.pNwTyGwArPpIpAqKuCyM2yOxFvVvKvOvPvEuFsWrNqHpIQGB$2.rTvTwWxX5yOuOrXrNrDqA7.NqHpF4.rIvXuS5.tXxXwCqP3.qXxI2yOxQvIuOvFwMxEwRvNuCsQrLqMpTpFPE$.KtRvBwLxSyN3yOwHtDsPsMqSB13.AtPuVD4.pJwQwMqD4.uJ2yOxWuFsStJvQyDyOyCwQvEtVsVsCrKqRpUXG$.rIsVuMwFxS4yOvIuEtRsMpG14.qPtQK5.tUwJpS4.pUxWyOxMsOqLqStExN3yOxEvSuVuHtStDsHrGqBpAH$VqNsHuGwHxX3yOxNwFvEuBrJV14.rQA5.qBvGqE5.vKyOxNrFC.qBvD3yOyHwBvFvIvMvHuRtTsOrGqBpCLC$EpXsAuHwOyC3yOyDxAvRtLqOPA12.qA7.tIqX5.qFyOyKqU3.rNyE3yOvEtPuEvUxAwVwCuXtSsMrIqIpLRE$.pOrXuOwTyCyN2yOyHxBvCsJqDWLIG9.N7.qQrJ6.xHyOrR4.vO3yOuIrMrPtWxHyOyLxIwAuUtTsUrVqWpVWF$.pJsDuSwRxSyFyKyMxHvQtRrXqQpXpRpOpCG8.pB7.rB6.tDyOtL4.rNyL2yOuBpQpErBvR3yOxRwJvPvCuKtOsMrFpWUF$.pKsGuOwCwVxFxMwKuVtVtHsPsArQrMrApWN7.pX7.rA7.yEuI5.wR2yOuD2.HsT3yOyLwBvKvUwFvXvDuBsQrDpTWJC$.pOsDtWuXvKvRvEtEsTtFtPtStQtOtBsAqKQ6.pSE5.OtFI6.vSuO5.tG2yOuK3.pKxP2yOyNuJsVtRvU2xLwOvItXsNrEqCpIQE$.pNrQsWtMtRuFrJrBrVtAuCuVvGuXuBsMqOVA3.CpHrTpLG2.SqQwSsMF5.uIvX5.PyNyOwH4.uH3yOtLqIqKsNwJ2yOxOwFvBtWsSrQqRpTUE$.pGqUrPrVsDrJpGqDrLtAuPvWwJvUuLsQrApPQGFTqOvCsNrApLpBqLsUxXyOrEF3.FtJyNA5.xF2yO4.TxV2yOtTS.pHtE3yOxMwDvNvDuHtHsGrCpURC$.RpUqGqIqVRLpUrLtHvGwQwXwCuKsXrVqWqEpWqKsEuRuNtQsLrSsQuNwOyOtTrBN.EpLsGyOpU5.tS2yO5.uT2yOtN3.pWxB2yOyHvQuXvNwDvUuUtQsJqWpOQFA$2.OUpCpV.JqArUtVvTwTwOvKuDtDsKsArVsFtKuWuUtT2tDtWvEuRxVtOsUrHpVpUrItHyOvEG4.pP2yO5.rDyLyNsN4.uG3yOuPsVtNvQ2xMwJuXtNsBqRpPpANE$4.GC.QqKsLuLvQvSuXuAtAsErRrSsGtHuNuCsJrHrIsJtTtHwAsRrQrVrRrQsMtQ2yOrK4.P2yO6.yGyOsQ4.pUyL2yOtQqQqTsUwO2yOxRwAuOtHsBrAqGpNTE$7.pAqWsWuGuHtKsMrPqQqBpVqIrIsPsLqNXNpJ2qXtOtLpCpSqPrFrQsExDyAuMpX2.FqH2yOsO5.xMyOuP5.wAyOyMsOIDpStN3yOyDwIvHuNtOsPrSqUpSRC$7.pHrFsRsVrTqNpQSB.ATqDqJQ3.CXrJqU3.QpIpStFvNtWsTqBSpLrW3yO5.uPyOxC5.rOyHyCrJ3.qCwW3yOwOvKvLvRvGuKtLsGqVpMNC$7.pIqKrJqMW7.I6.2S6.HsTsUsBsDrDrItCwJ2yOpK4.qL2yO6.xCxQrA4.tN3yOvTtOtVvN2wXwCuXtPsBqNpIPGA$8.WpL24.qDrVpJqWrXsKtDsTxIyKsXE3.pB2yO6.vXyErK4.pGyI2yOvQrOrItEwI2yMxLwCuRtHrUqPpTpENC$34.X.DqFrLrVqSuXwIuIqW2.BqT2yOB5.tSyOsS5.vW2yOwIqAXqLuByM2yOyAwKvJuKtGsGrKqNpMNA$37.pCpRKsGtDtStLqCIWsO2yOvX5.pLyOuL5.rO2yOvOF2.rBxG3yOxCvUvPvJuRtWtDsDqTpJI$39.pWqCsUrRsPqRqQtRyOyKyOpD5.yOwD6.yEyOuK4.uC3yOxDuUuTvTwJwCvLuOtLsCqLpCG$40.pOHrJsKsMuFvXxEyDsPD3.F2yO6.wPyOuD4.pSyF2yOxKtEsMuBwIxNxKwOvMuJtErPqDpCKB$41.JqXsMtStUvIwBtIrC3.pK2yOG5.uFyOvF5.vR2yOxCrOqDrEuCxLyOyHxAvTuXuBsSrJqHpKPC$41.EqHrTsBtDtEsBtHqCFIqW2yOvN5.qDyOxA5.sG2yOwGqA.OrCvK2yOyDwKvQvJuVtVsTrTqRpNOA$42.pGpWrAqBsSrUsRqOqCsKwI2yOpI5.2yO5.pB2yOvTM2.GsMxX2yOwEvBvIvWvTvCuFtGsAqNpGI$43.N.pFErRsKsGtLuQxTyKsVB3.B2yO6.2yOwD4.pMwG2yOvKtEtMvFwRwXwKvOuNtFrNpWSC$46.LqRsBtCtQwFwUuPqT3.pC2yOsA5.yNyOwT5.tRyJyMvNrIqTsPvRxVyGxPwOvLuEsKqPpHLA$47.pOqXrRuDuCvOtUpUGLqT3yOB4.uVyOxD5.pUxUyKwEqJPpRsVxA2yOxXwSwAvAtFrIqApCI$47.LpOsIrKuAtMsRqOqKsGvJyJyOqT4.pVyOxL6.wOyLvHpD2.pTuIyL2yOxDwGwIvTuAsDqWpXpAE$49.pG2qXsQsKsItBsUxDxXuFP3.F2yO6.vByOuG4.rAwX2yOxNvRvVwWwOuTtDsBrBpUO$49.2EqKrHrVsErGvDvSwUsGB2.pI2yOqS5.tOyOuA5.tTyDyOyCvEuQwHxXxMvSuJtIsEqSpED$51.UpNpXpGsVtBwFuQqOJNqT2yOyMB4.rCyOvB5.qDwIyGxRuHtEuSxQyOyNxCvUuOtFrOpSK$54.qXqMtDtOtHrAqOsG2yKyOqV5.yOwH6.uDxJwJsFqUsNwF4yOxEvOuBsFqFSC$54.CqErCsVsPsIsWtKxIxTtFpM3.AyOxF6.sPxCvHpT.pItNxT4yOxWwJuRsPqNpCIA$55.IrArMrQrUrQvIvSsSsPO2.GwPyO6.rBxTvKP2.pTvFyJ4yOyExDvGsTqTpNRC$55.RpJpPpNpCsTsVvAtDrPVHpHuByOpC6.xTwDQ3.rPwQ4yOyNyOxXvMsVrGqEpEH$59.qLpXsAqRsUrOqMrGuByOuUM5.vHvSI4.tLyC2yOyLxXyI2yOvNtFsBrApRK$60.W.qVsBsAsJuK2yOrSD4.rNuF5.pXwKyMyLyBxDxF3yOvTuFtGrXqFO$62.qBqVrHsRxGxTtPqXA3.pDtP6.uIxXxSwTvNvQxU3yOwVvVuPsTqOR$62.MpBqEtKvMsLsVqJG.CVvE6.sHxLwTuVtBsWvRyN4yOxPvStJqTR$64.pHsXrBrNsFqPpGpHqIwMpX5.pBxBwJtCqNpJsFxB6yOwKtQqSR$64.qGrQpAqTrUrNrGrWvCtCA5.vFwEsAS.CtRyB6yOwStOqNSB$64.N.BqArLsItIuFvApQ5.qXuRqX3.pXvLyJ6yOwStAqGXH$67.XqQsQuUyBsNS5.rRpD4.sCwJyE2yMyIyJ2yOwCsGqIpLQA$68.pSsPyFvDrRE4.O5.XtKwBxAxH2xFyC2yOuUrWqWqDpAB$68.pFvLxVuUqRB10.qMtJuNvAvDvHwJyJyOyFuBsMrXqVpGC$68.sPvDuRtVqGG10.qUsAsLsRtAuAwP2yOxBuQtXtBrJpIB$68.sKsHuAsXqRXD8.OpSqBqGqPrOtXxN2yOxJwJvMtWrQpG$67.pMpKrUsNsSrUqHQ11.EpDrIvAyG3yOxVwJuJrPX$68.pEqErNsXtDrNpD12.RsDwDyI3yOyGwRuMrFN$69.RrAtKuHsIpD12.pItIwOxXyJyKyNyBwVuEqKF$70.qVuDvNsKS12.qMuFwGxBxIxPyDyEwWtEpMB$70.rMuXvRrUK12.rPuDvFvQwBxAyJyOwErRWB$69.EsQvXuKrDK11.pHsCtHtSuFvIxK2yOuLqOUD$69.pKtCuCtGrCX11.qLrRsCsKtNwC2yOxNsVqIpBG$69.pXrTsGsLrPpXTLPXTE4.pAqTrBrDsBuU3yOvRsMqTpKG$69.XpTqTrXsGrKqNqQrFrEqEQ4.qJqWqRrJuE4yOvCtErKpMB$70.DpQrPtA2tEtPtQsSqWWA2.qErNrLrQuE4yOxOvQuArQpC$71.VrPtXvEvUwAvHtOrEpHLIqFsStCsXuS5yOxVwMuHrBG$71.NsBvDxBxTxMwGtXrQqJqBqXuMvHuWvT7yOwStLpM$71.WsUwEyEyOyHwPuNtCsPtNwLxPxFxI8yOvRrD$71.pQtOwRyJyOyIwVvNuXvRxS11yOxOsOF$71.qGtTwHxLxTxHwMwBwNyB12yOtOJ$71.qKtIuUvJvKvEvDvPxH12yOuID$71.qEsBsPsOsKsNtEuUxHyJ10yOvN$71.pLqGqBpPpNqArHuDwTyD5yOyNyKyMyOxKR$71.HC4.qDtNwGxTyM2yOyNxVxHxJxWyKqR$77.pQtIvLxDxXyExUwQvPvLwExHtF$77.pTsMuLvVwMwGvDtUtHtTvEuW$77.pLrLtEuDuFtIsCrGrJsKuC$77.KqHrNsArMqIpJpDpSrE$78.WpQpLO3.I!"),
        new Species("3U4b", "Urium brevis", "U Uridae", 13, 10, "3/4,1,1", 0.323, 0.0429, 1, 1,
                "A large, complex creature with internal structure, moving at a steady pace.",
                "14.FMQ3RPKD$11.CSpPqLqXrGrJrKrIqXqJpPUF$9.BXqKrXtGuEuR2uWuRuJtSsVrSqMpINA$8.KqLsRuQwHxJxWyAxXxVxRxGwGuQsUrGqApCI$7.TrTuUxG9yOxVvStRsBqTpSUE$6.RsOwQ13yOxEvSuOtIrXqNpHJ$5.BsMxS16yOxQwIuWtIrSqLpJM$5.rKxS6yO2yLyM8yOyAwLuXtMsFqXpVpAKC$4.pJwK5yOyMxUxKxExIxVyJ7yOxGvNuFtBrVqMpHMEB$4.tF6yOxAvEuPuUvKwLxQyK9yOxFvXuRsSqHREA$3.pBxM5yOxGtKrWrQsEtLvAwPxRyI6yOwSvIuTuOuRvFuUsIpSJ$3.sJyI5yOtCpNTpDpRrAtCuUwAwUxO2yBxUxFvWuVuIuOvCvHvMvWvQtRqWVD$3.wLyM4yOvI5.QqVsPtMuIvJwCwGwFwHwKwIwAwKxG2xSxQxIwGuGrPpQWNC$2.qPwU5yOpV6.pAqLrHsGtJuAtRtDsSqWpFWqTuGxK2yOxVxHwAtUrNqKqIqCpCA$2.uIwV4yOxD8.BVpQqIpX8.qBvN2yOxOwTvOtLrOrFrSrPqJM$2.tSwV4yOX21.AqQvJ2yOyLxTwPuSsSsEsOsMrKpE$.pQtMxAyN3yO19.pFuOwXxJwIwHyE3yOyCxCwIuKtGtFtDsGpND$.pBtOxByJ2yOsD20.LvU8yOwQwCwVvNuBtTtUsSpNLMI$.pJtVwTyA2yOqG22.tV7yOyMuRuFwIvUuOuNuSsSpUpMpPpAD$.pXuAwExQyOvXpVJ22.vJ2yLxIxFyM2yOwKsAsDwJwBvEvOvQsOrArDqSpLKD$.qFtHvHxUyOuAqIpBA8.pDC6.AvCwPuMtXwTyOyFwDsUrRvByAxSsTpQsUxRwIwFxIwEtFsRsLrKpVpDM$.pSrWvA2yOuCrVqB8.rIrNR7.rJ7yOuU2.VvAtLpTpXxEyFxByJyOwDuLuBtJsBqTpVM$.QrRwOyOyKvBtIqL7.sNwEtRqMS8.wI2yOyJxS2yOrH3.rPqDpDuTyOyI3yOwLvPvJuMsXrPpTF$.EtFyB2yOwDtNpE3.BGHrHyOxJvAsOqRS7.tS2yOvHrCtExIuS4.pVpFsUyL5yO2xHwWvJtOrKpE$.pAuWyN2yOwRrF4.HQpLxWyOxIvEtErRpE2.vQ3yOxGxV2yOxH3.pE4.TpRrWxV8yOxQvNtEqLL$.qMwG3yOvJC4.FSrU2yOxSvUuDtJqI2.pM8yOqC8.qLrQwXyL8yOxHuSrXpJ$.sBxB3yOrN6.qT3yOyCxQwDvFtCpM4.uQyOxPuPvVyOyI8.pUrWvSyByN8yOwGtKqNI$.tGxG3yOpC5.DxS3yOxHyExPwIvLvArR4.vOtP3.wM8.JrEuExAyAyL7yOxOuTrSV$OtRxJ2yOvKH5.qSyF5yOyArDqF3yOvMpQrN2yNuD12.qAsIvFwTxRyCyIyN5yOwCsVpM$WtUxK2yOsXB5.vExP5yOyL4.wXxJwAtRyD2yOqF11.PqLtAvAwGwQxCxUyL4yOxGtVqBA$pJuAxI2yOrU5.rXuTxHyL5yOrU5.O2.C2yOpN11.QqSsUuEuQvFwLxRyK3yOyGuQqLB$pXuAxAyMyOrDB3.pSsJvAxGyM6yOqV8.pByOsXW11.SqOrUsKtEuSwNxTyM3yOvHqUD$qFtPwHyKvIrAI3.pPsLuTwXyM3yOxWX.yJvK3.pQrK3.SqVqRpTNE2DB6.GpIqErFtBvHxFyL3yOvRrAC$qCtCvOyIvErNpGA2.GrOtXwLyK3yOvM8.xOyOE2.pGsAsOrJpQpMqEpB8.GpNrOuFwXyN3yOvWqWA$pTsNvBxSvFsIqHI3.rFtOwHyH3yOyC9.2yOvFsHsSuNvCuSsVsPtApD10.qJtQxG4yOvVqN$pLsHvAwDuPsRrDUDEpWsNuAxJyOyNyI2yOvO9.pV2yOxUwRwJwHwCvSsW11.pMuGyG4yOvHpS$pEsEtTuRtTsPrVpRVpOrPsStO3yOuX.uJvLqV9.2yOyMyHyGyEyOwRU11.pUwJ5yOuCR$SrPtAtJsPrXsHrEqJrDrIqWqXyM2yOvO3.G4.wM2yOuOyI2yOyL4yOtP12.tD6yOsB$FqVsGrRqWqRrVsLsB2sFrEqAvX3yO9.xW5yOxU4yOqQ11.pB6yOwEpN$.qCrHqJpMpPqVsEsWsSsLsHuGvGxW3yO9.uO8yOvRpE11.xV6yOsM$.pFqFpMTWpWqRsEsPsMtDyOyCqGTvMyOrW3.pHqI3.wT7yOxBpG10.AwT6yOvHQ$.DpBLAFpEpIqJrQ2sLyGyOsFpPpFqJpK3.sNxP8yO2yLyBqL2.B4.CB.HuC6yOxQqD$6.KFKpQrWsOuHxWwKsIrPqUpCA4.yK5yOyJxKxFxGwP2.A4.RpHpApIsU6yOxWrR$10.pSrSsDsTtSrBrTsDqLpPqEpL3.yM3yOyCxBwBvPvIpL6.WrFrCrEtS5yOyFxHsV$9.pL.pCqQ2rHqVrJsR2sEuHyOyDqErEvIvVuAtUvOvRuMsUpJ6.HtFuEtNuU2yOyLyNyHxIwCtN$9.qE2.CpSrGrQrIsHsUsQtH5yOuVqTrJtPtSrNpH7.sV2xCxD2yOxPxRxNwFuKtI$14.UqIqFqIrPsOsIsMtR3yOuH3rCpI7.ArQyD4yOxDvWvXuUsSqU$16.HGSqUsOsBrOrQrLrBqMpUpEH7.CsJ5yOxBuHtRtArAO$19.FqBrVsNsOsArMrDqQpWN6.FsG5yOwTtErPqXpE$20.rNuTuAtLsOsWsLrUqTpKNGJSUpKsN4yOyLwMsMpRT$19.MQpAqIpPqRrWsIrVrFqTqHqAqEqTsAuS4yOxVvSrV$20.JpFpOpRqGrErUrV2rRrUsDsMtNvRyOyMyJyBwVuNqV$22.FpFpUqKrDrOrRrVsHtIuLvXyKxSxFwSvOtGpI$20.GpMpH3.QpPqOrFsDtUwGxJwRvSuXtRrK$20.ApBpX4.WqCrBsFuLvXvEuAsXrQpJ$27.pGqSrRsKtVtHsGqUpN$22.G4.qPrWrRrErJqSpJ$26.V2pHpCWN!"),
        new Species("K4s", "Kronium solidus", "K Kronidae", 18, 10, "1,1/3", 0.24, 0.029, 1, 1,
                "A crown-shaped creature with an inner ring, drifting gently.",
                "9.GL2OGB$6.BpPrIsM2tDsPrVqTpPO$5.pKsRvRyD4yOxHvRtVrVpUE$4.qEuS10yOwFtLqRJ$3.OuF6yOyL5yOxHuCqTE$3.sFyG3yOxEuCtDtTvJxHyL3yOxHtQpX$2.pPwX3yOsC3.BqHtBvWyG3yOwIsFO$2.uP3yO7.pIsMvRyG2yOyLuFpS$.qMyI2yO9.pPtBwN3yOvWrA$.vGyOwN4.qJsHrQqEB2.qTuNyB2yOxCrSB$pPxMyOpN3.yLwSqMpUrIrA2.TsUxC2yOxRsFG$sHyGvEJ2.xWxC3.GrXL2.rQwL2yOxRsCE$sUxEtVJ.tVuXpN4.sPO2.qWwN2yOxErN$rIvRuFpFrIqRsHpU3.pDtT3.rAxP2yOvWqM$pItTuUwFqMrAsFrQpD2.yBqE3.tG3yOuApF$.tVyGwFvMtBrVrXsHtQyOsK3.ByG2yOxUrQB$.sWtVyLyOvBqTpFvJwDpD4.yB3yOuCpF$2.tBxCyOvJpDqWqM5.4yOwAqE$2.qTuUwVuAsRTB.BLsW4yOwLqM$3.rGsHvWuAsUsCsPuI4yOxHuNpX$4.2sCuIvMwSyLyOyLxWvWtJqOB$6.qJsKuKuStTsHpP$7.B!"),
        new Species("4Q5s", "Pentaquadrium solidus", "Q Quadridae", 36, 10, "1,2/3,1/3,2/3", 0.16, 0.01, 1, 1,
                "A striped, square-ish creature that needs a large kernel (R = 36).",
                "22.GpKqCpKJ$20.JrXuFuUuKtDqWpA$19.pXwQxHxCxJxHvWtTrQpUO$18.pPyOxHvOwXyLyOyLwIuPtBrQqHpDG$18.yOvTrQtGwN4yOyDwQvGtTsKrDpUO$17.yOuP2.sCxE7yOxWwQvBtDqTO$16.yOwD3.tD6yOyL4yOwXtOqHE$15.2yO4.yL4yOwXvTwDxJ4yOwNsRpN$14.wLyO4.uX4yOvEqWqMrSuUxW4yOvMsHpP$13.pIyL4.qO4yOxU3.GsMwV4yOxHuPsMqEG$12.pDyL5.2yOyL2yO5.qWvOyG3yOwLuSuAtBrGpIE$12.yO5.5yOsK5.pXuNwXxJwXvJ2tDtVuPuAsRrGpUL$11.yO5.6yO6.qJtQtVsWrGpDpIrGtVwDwSwAuStBqWQ$10.yO5.5yOyL5.BqTuFsKT4.pUvE3yOyBvWtGqME$9.yOpK4.yI4yOvW5.sCvTyOyBV5.pSwV5yOvErXpD$8.pSyO5.4yOsK5.uAyGyLyOwDO5.qTwX5yOwVtDqEB$8.yO5.4yOpN6.2yOxCyOuK6.qOwLyL5yOuPrIT$7.yO5.4yOqJ6.yD4yO7.qOvBwNxExRyG2yOwSsUqCB$6.yLE4.uS3yOwA6.tQ4yO7.BrDrQrNrSsCuSxM2yOvBrNpF$5.qWvT5.4yO6.pP3yOyI6.qEsRvJrV5.vEyIyOxRtTrDpIB$5.yO5.yI3yOpI5.rGyOyI2yO6.rIxJ2yOrI5.tJyG2yOwVuFsCpS$4.yOpU4.4yOsR4.EvWyDuUvOyOqO6.yOyGxMyOqR5.tT4yOyGvJrXO$3.yOxP3.O4yOxE5.yOxRqJqCuNyO6.yDyOvRyDxHpF4.GyB5yOyBtTV$2.wLyO3.qC5yO5.yDyO2.VxCsM5.tL4yOuF5.8yOuUQ$2.yO3.pU2yOyL2yO5.pUyOqW2.uKyLJ5.4yOuP4.pX5yOxPwNxCyOuUB$.yOpS2.B5yOpU5.xJyOpPQvWyOqC4.rL4yOrD4.pK5yOwAsFqWuPyOtJ$tQwDqHJQ5yOwF5.tGyOvErSyByL4.QvOxRwAxWyOrX5.2yOxM2yOxMG2.vByOpD$uNuKtDsP6yO5.pI2yOtT2yO5.yOwAqTsFxWyO5.tB5yO4.yOwQ$qTuIwF6yO6.yOyGrXyDyOpS4.yIyO2.rNyOE5.xH4yO4.vEyO$pItO2wFwIxE2yO6.2yOGsMyOxH4.qJyOJ2.wAvW5.rL4yOpU3.pFyO$.rNrQrLsHvOyOwN5.xUyOE.wQyOO4.yIwSG.tByOpX5.xW3yOtV4.yO$4.pSuNyIqT4.sUyDuNqRxWyOsU4.yByOsPpXvJyOtO5.4yOyL4.yOrL$4.LtOsUrVO3.uXyDtLyBxEsW3.B2yOwFtJ2yOsU4.L5yO4.uSyO$6.qJsFrLqEpIuAyLsRtGsHpS4.2yOvGsC2yOrX4.B5yO5.yO$7.qOrSsHvRyOxCpN6.tLyOtV.yGyOqE5.vO4yO5.yO$8.OqJvWxMtTO6.xEyO.sPyOtV5.J4yO5.wXqH$9.qJrXrNrVpU5.qEwXsRsC2yO6.4yO5.pXyO$11.JpXrG5.qHvTwS2yO6.4yO6.yO$13.qWqE4.qJsUyGxP6.4yOpI5.yO$13.QqRqWqJpSqWrSxJuX6.4yOuN5.yO$15.pDqEsPvWyOvT6.xR4yO5.yO$17.rIwAyLrQ5.uU2yOyByOsH4.yOqE$16.GqOtLuKrV5.2yOyD2yOB3.uXvB$17.GqCpNsHpX3.5yOrI3.vGwF$20.rGsK2rN5yOxJ3.tTyO$20.JqWtB3yI3yO3.pKyO$22.tTuAuNwAyGyOtJ3.yO$22.VpAqJtVxMyOrN2OyO$24.OsUwQvEtLsWyO$24.BsKtLsWtGxHtV$25.rNqMqCtLqR$25.B!"),
        new Species("V4", "Tetravolvium", "V Volvidae", 18, 10, "1,1/3", 0.24, 0.0279, 1, 1,
                "A small four-lobed creature that stays in place.",
                "13.rSvBxR2yOyBuNrLG$11.BsFwAyL5yOyD2tLqJG2.T$5.BpFpIJ2.rXwNyOtGpI2.qJuXxRxHuUwAtLrApXqCqE$4.TrLsFrApA.sRxEwIrS6.qCsMuAuIxMvOtDsFsH$3.pItDuSuKsFqCvGyLyIpU8.EVqHsMyOwXuXuFuN$2.pDtLwIwXwDtGxU2yOJ4.sMwNyLsPE3.ErQyOxUwNwFpN$.GsPwNyByOxUxW2yOpD4.pUvT3yOrD5.uSyOyGxUvB$.qTvRxW5yOwS5.rLyGwVtQxRuS5.pUyI2yOyL$BtTxMyG5yO6.rX2yOvEwDuF4.pAqJvM3yOpK$pFwIyLxWyL3yOyI6.pN2yOxWuKqT2.pUqWrQsRvExW2yOqW$qMyLyOxJxPxHxCwSwF8.OpFJ3.tBuAuKvBwIxE2yOrG$rD2yOxCwIvBuKuAtD3.JpDO8.wFwSwXxHxPxJyOyLqM$qT2yOxUvEsRrSrApU2.qTuKxU2yOpN6.yI3yOyLxWyIwIpF$pI3yOvJqJpA4.uIwAvG2yOrX6.5yOyDxJtTB$.yI2yOyDpU5.uUxPtQwXyGrL5.xC5yOxUvRqT$.vBxRyGyOuP5.rG3yOvTpU4.pD2yOxWxUyOxWwNsPG$.pKwDwNxRyOrNE3.EsRyLwNsM4.J2yOxUtGwFwXwItLpD$2.uKuFuXwXyOsKqHTE8.pSyIyOvJqEsFuKuStDpI$3.sHsCtDvOxHuFtVsKqC6.rQwIwXsUBpArAsHrNT$3.qEqCpXrAtLvWuUxExMuUqH2.pItGyOwNsC2.JpIpFB$4.Q2.JqMtOtLyD5yOyLwAsFB$10.LrNuPyD2yOxPvBrS!"),
        new Species("2D6r", "Hexadentium rotans", "D Dentidae", 18, 10, "1,3/4", 0.27, 0.039, 1, 1,
                "A six-toothed ring that stays in place while its body turns.",
                "11.JtByOyDwNtTqE$9.EtO4yOyDwDsUO$8.VuU6yOyLwVtQpD2.EpIqJqCG$7.GsMyL9yOxWuUrGpUsUvTxUxPtG$7.pFtLyL19yOO$7.VsW4yOyIwFsWpPErI11yO$7.qM6yOwIrI3.qCxM9yOxW$6.rG7yOxMsF3.JuF4yOyL4yOxMrN$2.2pSqHtJyG7yOyItG3.GtT4yOxPxR3yOwNsW$2.tTuFvWyL2yOvEtLuUyD2yOyLtJ3.LuI3yOyBvMuU3yOvJqR$.wQxHxW3yOrL2.pArVvTxUtB4.BsW2yOyGvMrVqEwD2yOuNG$.yL4yOuI4.BpD8.sCxHuSqT2.pA2yOuP$tQ4yOyBqW21.2yOwA$xP4yOwStVrIO18.O2yOyI$yL4yOyDxPwSvErQ16.ErG3yOqEG$xC8yOxE15.pNsFwN3yOvWrGpA$uK9yO14.qOwA6yOvRsF$rLxE7yOvB14.uI8yOwDL$VsPuN4yOxPuAqJ14.vE9yOpN$.pAqC3yOxErIQ15.tLxCyDyI2yL4yOqC$3.wA2yOsMB17.rAtOvEwLyD4yOpK$3.sW2yOpX20.qJuN5yO$3.rQ2yOsK3.pKtD16.wV5yO$3.rQyByOxU2.rDvRyGyOsM6.qCsPqEB3.sP3yOyIyDqO$2.EsMxP2yOsPsHvTyI2yOxJpX4.wV2yOwLrXpNpDrV2yOyLwVvTvM$2.qEtVxU2yOxJwIyB3yOxEpX3.qWxP4yOxE3yOyDuNsCrQ$3.vMyG3yOyI4yOwSpP3.qJwX7yOyDrX$3.wN9yOyLqR3.pIvOyL6yOqE$4.10yOvJpD2.pKuPxW5yOrA$4.pA11yOxPtVuIwIyD4yOvGqMB$5.OwX3yOwNwQ13yOwAqO$7.qRuCuKsMpUBpAsUvWyB8yOuCO$9.JL4.pFtBwIyI5yOuXO$16.pNtGwLyG3yOrG$17.pU2rSqC!"),
        new Species("3B5v", "Pentabullium vagus", "B Bullidae", 27, 10, "1,1,2/3", 0.33, 0.042, 1, 1,
                "A bubble-like creature with nested rings that wanders slowly.",
                "15.H2QIA$12.OqRtBuVvQvLuKsUqXpGC$10.JrKvW8yOxKtVqLF$9.pPuV13yOuXqEA$8.qPxT16yOtDU$7.rB19yOwCqF$6.qU21yOyKrTK$5.qK24yOtFpC$4.qBxS9yOyFxOxIxQyG11yOuIpK$3.pQwP8yOyLxEvEtMsTtJuWwPxW10yOuTR$2.pEvI8yOyJxHvCsJqGpPqUtDvCwCwOxL9yOtG$.JtW8yOyAxXyBwWuIsCsNuUyG2yOvNtGuMyE8yOH$.rT7yOyMwLwJ11yOqV.rT8yOrM$LwX7yOvErOvE11yOtE2.tV7yOvR$qS7yOxLV.uQ12yO3.8yO$tF7yOuH2.vJ3yOyCuAqSrAvC4yOqD2.xI7yO$uP7yOrD2.yI2yOyMpB4.tB4yOR.uI7yO$uU7yOpD.pC3yOwL6.4yOuTCtA7yO$tU7yOV.tC3yOvI6.5yOqNuM7yO$rW7yOqAX5yO6.wO4yOvQ8yO$pI7yOsNqL5yO6.uG4yOyAxX6yOyN$.7yOvArS5yOpS4.qJ4yOyCuOvN6yOxA$.8yOsGyJ5yOuRsEsMvP4yOyKtJErV5yOxRtT$.xK7yOsCuJxP12yOuK2.L4yOxQvBpB$.pR7yOsN.qLwA10yOxI3.sI3yOyAuRqN$2.xP6yOwE3.uM9yOuG2.rF3yOyIuRpP$2.sUxNyI5yON3.vA7yOyDtLpCtO3yOyLuTW$3.tKwDxL5yOrJM.qQwVyJ2yOyNyKyIyOyIyG3yOyLuPG$4.qLtMwByH5yOuOuCxVxWxFwTwRxC6yOyKuH$6.qBuAxB9yOwQwL6yOyKuB$8.qVuTxP14yOyNuN$10.sAvPyD12yOvQT$11.pBtHwRyN8yOyKvXqP$13.rAuWxGyHyN2yOyMyBwNtVpU$15.qQsNtLtStLsLqP!"),
        new Species("2L2i", "Dilapillium inversus", "L Lapillidae", 18, 10, "1,1/3", 0.31, 0.042, 1, 1,
                "A small doughnut-shaped 'pebble' that sits almost perfectly still.",
                "8.SWF$4.FtQwAxPyJyNyHxEuUrN$3.tN10yOxDqK$2.vI5yOyBwHxE5yOsA$.rU5yOsWpLNpArN5yOpC$.5yOuPsH3.pEsI5yO$pL4yOyHwPsMqRUpSrGvG5yOR$uK4yOvXyMxHwTwPwRxH6yOtE$wP4yOvRyKxW3xUyC6yOvJ$vP4yOvOyHxLxIxJxLxV6yOuL$rR4yOwKxDuMsAsDtPvDvO5yOqQ$D5yOtQqH3.pRsJ5yOE$.wI4yOvMpJ3.pKvW4yOtR$2.5yOwFsSrPsQwJ4yOyD$2.CyC11yOxF$4.uIxAyG5yOyFwKsN$5.SrItAuBuHtTsJqA!")
    };

    // =====================================================================
    //  Parameters, encoding
    // =====================================================================

    static final class Params {
        double R = 13, T = 10, m = 0.15, s = 0.015;
        double[] b = {1};
        int kn = 1, gn = 1;
        Params copy() { Params p = new Params(); p.R = R; p.T = T; p.m = m; p.s = s; p.b = b.clone(); p.kn = kn; p.gn = gn; return p; }
    }

    static double[] parseB(String st) {
        String[] parts = st.replace("[", "").replace("]", "").split(",");
        double[] b = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.contains("/")) { String[] q = p.split("/"); b[i] = Double.parseDouble(q[0]) / Double.parseDouble(q[1]); }
            else b[i] = Double.parseDouble(p);
        }
        return b;
    }

    static String bToString(double[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(',');
            double v = b[i];
            String frac = null;
            for (int den = 1; den <= 12 && frac == null; den++) {
                double num = v * den;
                if (Math.abs(num - Math.round(num)) < 1e-9) frac = den == 1 ? String.valueOf(Math.round(num)) : Math.round(num) + "/" + den;
            }
            sb.append(frac != null ? frac : String.valueOf(v));
        }
        return sb.toString();
    }

    /** Decode Bert Chan's run-length encoding of a 2-D pattern (rows end with '$'). */
    static double[][] decodeRLE(String st) {
        List<double[]> rows = new ArrayList<>();
        List<Double> row = new ArrayList<>();
        String last = "", count = "";
        st = st.replaceAll("!$", "") + "$";
        for (char ch : st.toCharArray()) {
            if (Character.isDigit(ch)) count += ch;
            else if ("pqrstuvwxy@".indexOf(ch) >= 0) last = String.valueOf(ch);
            else {
                String tok = last + ch;
                int c = count.isEmpty() ? 1 : Integer.parseInt(count);
                if (tok.equals("$")) {
                    double[] r = new double[row.size()];
                    for (int i = 0; i < r.length; i++) r[i] = row.get(i);
                    rows.add(r);
                    for (int k = 1; k < c; k++) rows.add(new double[0]);
                    row = new ArrayList<>();
                } else {
                    double v = chToVal(tok) / 255.0;
                    for (int k = 0; k < c; k++) row.add(v);
                }
                last = ""; count = "";
            }
        }
        int w = 0;
        for (double[] r : rows) w = Math.max(w, r.length);
        double[][] out = new double[rows.size()][w];
        for (int y = 0; y < rows.size(); y++) System.arraycopy(rows.get(y), 0, out[y], 0, rows.get(y).length);
        return out;
    }

    static int chToVal(String c) {
        if (c.equals(".") || c.equals("b")) return 0;
        if (c.equals("o")) return 255;
        if (c.length() == 1) return c.charAt(0) - 'A' + 1;
        return (c.charAt(0) - 'p') * 24 + (c.charAt(1) - 'A' + 25);
    }

    static String valToCh(int v) {
        if (v == 0) return ".";
        if (v < 25) return String.valueOf((char) ('A' + v - 1));
        return "" + (char) ('p' + (v - 25) / 24) + (char) ('A' + (v - 25) % 24);
    }

    /** Encode a pattern in Chan's run-length format, so specimens can be shared with his tools. */
    static String encodeRLE(double[][] a) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < a.length; y++) {
            if (y > 0) sb.append('$');
            int x = 0, w = a[y].length;
            while (x < w) {
                int v = (int) Math.round(a[y][x] * 255);
                int run = 1;
                while (x + run < w && (int) Math.round(a[y][x + run] * 255) == v) run++;
                if (run > 1) sb.append(run);
                sb.append(valToCh(v));
                x += run;
            }
        }
        return sb.append('!').toString();
    }

    // =====================================================================
    //  FFT and the Lenia world
    // =====================================================================

    static final class FFT2D {
        final int n;
        private final double[] cos, sin;
        private final int[] rev;
        private final ThreadLocal<double[][]> scratch;

        FFT2D(int n) {
            this.n = n;
            cos = new double[n / 2]; sin = new double[n / 2];
            for (int k = 0; k < n / 2; k++) { cos[k] = Math.cos(2 * Math.PI * k / n); sin[k] = Math.sin(2 * Math.PI * k / n); }
            rev = new int[n];
            int bits = Integer.numberOfTrailingZeros(n);
            for (int i = 0; i < n; i++) rev[i] = Integer.reverse(i) >>> (32 - bits);
            scratch = ThreadLocal.withInitial(() -> new double[2][n]);
        }

        private void fft1d(double[] re, double[] im, boolean inverse) {
            for (int i = 0; i < n; i++) {
                int j = rev[i];
                if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
            }
            for (int len = 2; len <= n; len <<= 1) {
                int half = len >> 1, step = n / len;
                for (int i = 0; i < n; i += len)
                    for (int k = 0; k < half; k++) {
                        double wr = cos[k * step], wi = inverse ? sin[k * step] : -sin[k * step];
                        int a = i + k, b = a + half;
                        double xr = re[b] * wr - im[b] * wi, xi = re[b] * wi + im[b] * wr;
                        re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi;
                    }
            }
        }

        void forward(double[] re, double[] im) { transform(re, im, false); }

        void inverse(double[] re, double[] im) {
            transform(re, im, true);
            double s = 1.0 / ((double) n * n);
            for (int i = 0; i < re.length; i++) { re[i] *= s; im[i] *= s; }
        }

        private void transform(double[] re, double[] im, boolean inv) {
            IntStream.range(0, n).parallel().forEach(y -> {
                double[][] t = scratch.get();
                System.arraycopy(re, y * n, t[0], 0, n); System.arraycopy(im, y * n, t[1], 0, n);
                fft1d(t[0], t[1], inv);
                System.arraycopy(t[0], 0, re, y * n, n); System.arraycopy(t[1], 0, im, y * n, n);
            });
            IntStream.range(0, n).parallel().forEach(x -> {
                double[][] t = scratch.get();
                for (int y = 0; y < n; y++) { t[0][y] = re[y * n + x]; t[1][y] = im[y * n + x]; }
                fft1d(t[0], t[1], inv);
                for (int y = 0; y < n; y++) { re[y * n + x] = t[0][y]; im[y * n + x] = t[1][y]; }
            });
        }
    }

    static final class World {
        final int n;
        final FFT2D fft;
        final double[] A, re, im, kre, kim, potential;
        Params p = new Params();
        long gen = 0;
        double time = 0;
        private Params builtFor = null;

        World(int n) {
            this.n = n;
            fft = new FFT2D(n);
            int nn = n * n;
            A = new double[nn]; re = new double[nn]; im = new double[nn];
            kre = new double[nn]; kim = new double[nn]; potential = new double[nn];
        }

        /** Kernel core functions, numbered as in Chan's code (kn = 1..4). */
        static double kernelCore(int kn, double r) {
            switch (kn) {
                case 1: { double v = 4 * r * (1 - r); return v * v * v * v; }                 // polynomial
                case 2: { if (r <= 0 || r >= 1) return 0; return Math.exp(4 - 1 / (r * (1 - r))); } // exponential bump
                case 3: return (r >= 0.25 && r <= 0.75) ? 1 : 0;                               // step
                default: return (r >= 0.25 && r <= 0.75) ? 1 : (r < 0.25 ? 0.5 : 0);            // staircase (Life-like)
            }
        }

        /** Growth functions, numbered as in Chan's code (gn = 1..3). */
        static double growth(int gn, double u, double m, double s) {
            switch (gn) {
                case 1: { double v = Math.max(0, 1 - (u - m) * (u - m) / (9 * s * s)); return v * v * v * v * 2 - 1; }
                case 2: return Math.exp(-(u - m) * (u - m) / (2 * s * s)) * 2 - 1;
                default: return Math.abs(u - m) <= s ? 1 : -1;
            }
        }

        /** Radial kernel profile at distance r (in units of R), before normalisation. */
        static double shell(Params p, double r) {
            if (r >= 1) return 0;
            int B = p.b.length;
            double Br = B * r;
            int idx = Math.min((int) Math.floor(Br), B - 1);
            return kernelCore(p.kn, Br % 1.0) * p.b[idx];
        }

        void rebuildKernelIfNeeded() {
            Params q = builtFor;
            if (q != null && q.R == p.R && q.kn == p.kn && Arrays.equals(q.b, p.b)) return;
            double sum = 0;
            for (int y = 0; y < n; y++) {
                int dy = y < n / 2 ? y : y - n;
                for (int x = 0; x < n; x++) {
                    int dx = x < n / 2 ? x : x - n;
                    double v = shell(p, Math.sqrt(dx * dx + dy * dy) / p.R);
                    kre[y * n + x] = v;
                    sum += v;
                }
            }
            for (int i = 0; i < kre.length; i++) { kre[i] /= sum; kim[i] = 0; }
            fft.forward(kre, kim);
            builtFor = p.copy();
        }

        void step() {
            rebuildKernelIfNeeded();
            int nn = n * n;
            System.arraycopy(A, 0, re, 0, nn);
            Arrays.fill(im, 0);
            fft.forward(re, im);
            for (int i = 0; i < nn; i++) {
                double a = re[i], b = im[i], c = kre[i], d = kim[i];
                re[i] = a * c - b * d; im[i] = a * d + b * c;
            }
            fft.inverse(re, im);
            final double dt = 1.0 / p.T, m = p.m, s = p.s;
            final int gn = p.gn;
            IntStream.range(0, n).parallel().forEach(y -> {
                for (int i = y * n, e = i + n; i < e; i++) {
                    double u = re[i];
                    potential[i] = u;
                    double v = A[i] + dt * growth(gn, u, m, s);
                    A[i] = v < 0 ? 0 : v > 1 ? 1 : v;
                }
            });
            gen++;
            time += dt;
        }

        void clear() { Arrays.fill(A, 0); gen = 0; time = 0; }

        void place(double[][] cells, int cx, int cy) {
            int h = cells.length, w = h == 0 ? 0 : cells[0].length;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double v = cells[y][x];
                    if (v <= 0) continue;
                    int gx = Math.floorMod(cx - w / 2 + x, n), gy = Math.floorMod(cy - h / 2 + y, n);
                    A[gy * n + gx] = v;
                }
        }

        void brush(double gx, double gy, double radius, boolean add, Random rng) {
            int r = (int) Math.ceil(radius);
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++) {
                    double d = Math.hypot(dx, dy);
                    if (d > radius) continue;
                    int x = Math.floorMod((int) Math.round(gx) + dx, n), y = Math.floorMod((int) Math.round(gy) + dy, n);
                    int i = y * n + x;
                    if (add) A[i] = Math.min(1, A[i] + 0.5 * rng.nextDouble() * (1 - d / radius) + 0.2);
                    else A[i] = 0;
                }
        }

        /** Scatter random patches of life, as Chan's own software does: 15-40 patches about 0.9 R across. */
        void randomSoup(Random rng) {
            clear();
            double scale = (double) n * n / (256.0 * 256.0);
            int patches = (int) Math.max(15, Math.round((15 + rng.nextInt(26)) * scale));
            int size = Math.max(3, (int) (p.R * 0.9));
            for (int k = 0; k < patches; k++) {
                int cx = rng.nextInt(n), cy = rng.nextInt(n);
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++)
                        A[Math.floorMod(cy + y, n) * n + Math.floorMod(cx + x, n)] = rng.nextDouble() * 0.9;
            }
        }

        double mass() { double s = 0; for (double v : A) s += v; return s; }

        /** Centre of mass on the torus (circular mean). */
        double[] centroid() {
            double cx = 0, sx = 0, cy = 0, sy = 0, tot = 0;
            double[] cs = new double[n], sn = new double[n];
            for (int i = 0; i < n; i++) { cs[i] = Math.cos(2 * Math.PI * i / n); sn[i] = Math.sin(2 * Math.PI * i / n); }
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double v = A[y * n + x];
                    if (v == 0) continue;
                    cx += v * cs[x]; sx += v * sn[x]; cy += v * cs[y]; sy += v * sn[y]; tot += v;
                }
            if (tot < 1e-9) return null;
            return new double[]{(Math.atan2(sx, cx) / (2 * Math.PI) * n + n) % n, (Math.atan2(sy, cy) / (2 * Math.PI) * n + n) % n};
        }

        /** Crop the live region around the centre of mass, for the notebook. */
        double[][] cropAround(double[] c) {
            if (c == null) return null;
            int half = n / 2;
            int cx = (int) Math.round(c[0]), cy = (int) Math.round(c[1]);
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int dy = -half; dy < half; dy++)
                for (int dx = -half; dx < half; dx++) {
                    double v = A[Math.floorMod(cy + dy, n) * n + Math.floorMod(cx + dx, n)];
                    if (v > 0.001) { minX = Math.min(minX, dx); maxX = Math.max(maxX, dx); minY = Math.min(minY, dy); maxY = Math.max(maxY, dy); }
                }
            if (minX > maxX) return null;
            int w = maxX - minX + 1, h = maxY - minY + 1;
            double[][] out = new double[h][w];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    out[y][x] = A[Math.floorMod(cy + minY + y, n) * n + Math.floorMod(cx + minX + x, n)];
            return out;
        }
    }

    static double[][] rotate90(double[][] a) {
        int h = a.length, w = h == 0 ? 0 : a[0].length;
        double[][] r = new double[w][h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) r[x][h - 1 - y] = a[y][x];
        return r;
    }

    static double[][] flip(double[][] a) {
        double[][] r = new double[a.length][];
        for (int y = 0; y < a.length; y++) {
            r[y] = new double[a[y].length];
            for (int x = 0; x < a[y].length; x++) r[y][x] = a[y][a[y].length - 1 - x];
        }
        return r;
    }

    // =====================================================================
    //  Colours
    // =====================================================================

    enum ColourMap {
        TWILIGHT("Twilight"), MICROSCOPE("Microscope"), EMBER("Ember"), INK("Ink");
        final String label;
        ColourMap(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    static final double[][] TWILIGHT_STOPS = {
        {0.00, 4, 6, 60}, {0.25, 70, 20, 110}, {0.50, 190, 60, 120}, {0.75, 250, 160, 120}, {1.00, 255, 245, 225}};
    static final double[][] MICRO_STOPS = {
        {0.00, 236, 240, 232}, {0.30, 160, 205, 190}, {0.60, 60, 140, 150}, {0.85, 30, 70, 110}, {1.00, 15, 30, 60}};
    static final double[][] EMBER_STOPS = {
        {0.00, 0, 0, 0}, {0.30, 110, 10, 20}, {0.60, 230, 80, 10}, {0.85, 255, 200, 60}, {1.00, 255, 255, 220}};

    static int clamp255(double v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }

    static int ramp(double t, double[][] st) {
        if (t <= st[0][0]) return (clamp255(st[0][1]) << 16) | (clamp255(st[0][2]) << 8) | clamp255(st[0][3]);
        for (int i = 1; i < st.length; i++)
            if (t <= st[i][0]) {
                double[] a = st[i - 1], b = st[i];
                double w = (t - a[0]) / (b[0] - a[0]);
                return (clamp255(a[1] + w * (b[1] - a[1])) << 16) | (clamp255(a[2] + w * (b[2] - a[2])) << 8) | clamp255(a[3] + w * (b[3] - a[3]));
            }
        double[] z = st[st.length - 1];
        return (clamp255(z[1]) << 16) | (clamp255(z[2]) << 8) | clamp255(z[3]);
    }

    static final int[][] LUT = new int[ColourMap.values().length][256];
    static {
        for (int i = 0; i < 256; i++) {
            double t = i / 255.0;
            LUT[0][i] = ramp(t, TWILIGHT_STOPS);
            LUT[1][i] = ramp(t, MICRO_STOPS);
            LUT[2][i] = ramp(t, EMBER_STOPS);
            int g = clamp255(250 - 235 * t);
            LUT[3][i] = (g << 16) | (g << 8) | clamp255(g * 0.96);
        }
    }

    static void render(World w, ColourMap map, int[] pixels) {
        int[] lut = LUT[map.ordinal()];
        double[] A = w.A;
        for (int i = 0; i < A.length; i++) pixels[i] = lut[(int) (A[i] * 255.999)];
    }

    // =====================================================================
    //  Notebook
    // =====================================================================

    static final class Specimen {
        String name, notes;
        Params params;
        double[][] cells;
        Specimen(String name, String notes, Params params, double[][] cells) {
            this.name = name; this.notes = notes; this.params = params; this.cells = cells;
        }
        @Override public String toString() { return name; }

        String toJson() {
            Params p = params;
            return String.format(Locale.ROOT,
                "{\"code\":\"%s\",\"name\":\"%s\",\"notes\":\"%s\",\"params\":{\"R\":%s,\"T\":%s,\"b\":\"%s\",\"m\":%s,\"s\":%s,\"kn\":%d,\"gn\":%d},\"cells\":\"%s\"}",
                esc(name.replaceAll("\\s+", "")), esc(name), esc(notes), num(p.R), num(p.T), bToString(p.b), num(p.m), num(p.s), p.kn, p.gn, encodeRLE(cells));
        }
        static String num(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }
        static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    }

    /** A minimal JSON reader: enough to load Chan's animals.json and our own notebooks. */
    static final class Json {
        private final String s;
        private int i = 0;
        Json(String s) { this.s = s; }

        static Object parse(String s) { Json j = new Json(s); j.ws(); return j.value(); }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++; ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws(); String k = str(); ws(); i++; // ':'
                    m.put(k, value()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    i++; return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++; ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    i++; return l;
                }
            }
            if (c == '"') return str();
            int st = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) i++;
            String tok = s.substring(st, i);
            if (tok.equals("true")) return Boolean.TRUE;
            if (tok.equals("false")) return Boolean.FALSE;
            if (tok.equals("null")) return null;
            return Double.parseDouble(tok);
        }

        private String str() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
            i++;
            return sb.toString();
        }
    }

    // =====================================================================
    //  Application state
    // =====================================================================

    private volatile World world;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true, singleStep = false, placeMode = false;
    private volatile int stepsPerFrame = 2;
    private volatile ColourMap colourMap = ColourMap.TWILIGHT;
    private volatile BufferedImage frame;
    private volatile double stepsPerSecond, speed, lastMass;
    private volatile int rotation = 0;
    private volatile boolean flipped = false;
    private final Random rng = new Random();

    private final double[] massHistory = new double[400];
    private int massCount = 0, massHead = 0;
    private final Object histLock = new Object();
    private final double[] potentialHist = new double[60];

    private final DefaultListModel<Specimen> notebook = new DefaultListModel<>();

    private final ViewPanel view = new ViewPanel();
    private final CurvesPanel curves = new CurvesPanel();
    private final MassPlot massPlot = new MassPlot();
    private final JLabel status = new JLabel(" ");
    private final JTextArea blurb = new JTextArea(3, 26);

    private final JComboBox<Species> speciesBox = new JComboBox<>(GALLERY);
    private final JComboBox<String> rotBox = new JComboBox<>(new String[]{"0°", "90°", "180°", "270°"});
    private final JCheckBox flipBox = new JCheckBox("Mirror");
    private final JToggleButton placeButton = new JToggleButton("Place by clicking");
    private final ParamSlider muSlider = new ParamSlider("Growth centre  μ", 0.01, 0.50, 0.15, "%.4f");
    private final ParamSlider sigmaSlider = new ParamSlider("Growth width  σ", 0.001, 0.08, 0.015, "%.4f");
    private final ParamSlider tSlider = new ParamSlider("Time resolution  T", 1, 100, 10, "%.0f");
    private final ParamSlider rSlider = new ParamSlider("Kernel radius  R", 5, 50, 13, "%.0f");
    private final ParamSlider speedSlider = new ParamSlider("Steps per frame", 1, 20, 2, "%.0f");
    private final JComboBox<Integer> sizeBox = new JComboBox<>(new Integer[]{128, 256, 512});
    private final JComboBox<ColourMap> mapBox = new JComboBox<>(ColourMap.values());
    private final JToggleButton playButton = new JToggleButton("Pause", true);
    private final JLabel ruleLabel = new JLabel(" ");
    private boolean suppress = false;

    public LeniaLab() {
        super("Lenia Lab — a naturalist's guide to smooth artificial life");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        world = new World(256);
        sizeBox.setSelectedItem(256);
        buildUI();
        loadSpecies(GALLERY[0], true);

        Thread worker = new Thread(this::loop, "lenia-sim");
        worker.setDaemon(true);
        worker.start();
        new javax.swing.Timer(50, e -> { view.repaint(); curves.repaint(); massPlot.repaint(); updateStatus(); }).start();
        pack();
        setLocationRelativeTo(null);
    }

    private void loop() {
        World current = null;
        int[] pixels = null;
        BufferedImage[] bufs = new BufferedImage[2];
        int bi = 0, frameNo = 0;
        long lastT = System.nanoTime(), stepsSince = 0;
        double[] lastC = null;
        double lastTime = 0, dist = 0, distTime = 0;
        while (true) {
            long start = System.nanoTime();
            Runnable t;
            while ((t = tasks.poll()) != null) t.run();
            World w = world;
            if (w != current) {
                current = w;
                bufs[0] = new BufferedImage(w.n, w.n, BufferedImage.TYPE_INT_RGB);
                bufs[1] = new BufferedImage(w.n, w.n, BufferedImage.TYPE_INT_RGB);
                pixels = new int[w.n * w.n];
                lastC = null;
            }
            boolean advance = running || singleStep;
            if (advance) {
                int steps = singleStep && !running ? 1 : stepsPerFrame;
                singleStep = false;
                for (int k = 0; k < steps; k++) w.step();
                stepsSince += steps;
            }
            render(w, colourMap, pixels);
            BufferedImage img = bufs[bi];
            bi ^= 1;
            img.setRGB(0, 0, w.n, w.n, pixels, 0, w.n);
            frame = img;

            if (frameNo % 3 == 0) {
                double m = w.mass();
                lastMass = m;
                if (advance) synchronized (histLock) {
                    massHistory[massHead] = m;
                    massHead = (massHead + 1) % massHistory.length;
                    massCount = Math.min(massCount + 1, massHistory.length);
                }
                // Speed of the centre of mass, in cells per unit of time, smoothed
                double[] c = w.centroid();
                if (c != null && lastC != null && w.time > lastTime) {
                    double dx = c[0] - lastC[0], dy = c[1] - lastC[1];
                    dx -= Math.round(dx / w.n) * w.n; dy -= Math.round(dy / w.n) * w.n;
                    dist = dist * 0.9 + Math.hypot(dx, dy);
                    distTime = distTime * 0.9 + (w.time - lastTime);
                    speed = distTime > 0 ? dist / distTime : 0;
                } else if (c == null) speed = 0;
                lastC = c;
                lastTime = w.time;
                // Histogram of the potential where there is life, for the growth plot
                double[] h = new double[potentialHist.length];
                for (int i = 0; i < w.A.length; i += 3) {
                    if (w.A[i] < 0.01) continue;
                    int bin = (int) (w.potential[i] / 0.5 * h.length);
                    if (bin >= 0 && bin < h.length) h[bin]++;
                }
                synchronized (potentialHist) { System.arraycopy(h, 0, potentialHist, 0, h.length); }
            }
            frameNo++;

            long now = System.nanoTime();
            if (now - lastT > 500_000_000L) { stepsPerSecond = stepsSince * 1e9 / (now - lastT); stepsSince = 0; lastT = now; }
            long sleep = 25 - (System.nanoTime() - start) / 1_000_000;
            try { Thread.sleep(Math.max(2, sleep)); } catch (InterruptedException ex) { return; }
        }
    }

    private void resetHistory() {
        synchronized (histLock) { massCount = 0; massHead = 0; }
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    private double[][] orientedPattern(double[][] p) {
        double[][] q = flipped ? flip(p) : p;
        for (int k = 0; k < rotation; k++) q = rotate90(q);
        return q;
    }

    /** Set the rule from a species; optionally clear the world and put one in the middle. */
    private void loadSpecies(Species sp, boolean resetWorld) {
        Params p = sp.params();
        setSliders(p);
        blurb.setText(sp.blurb());
        double[][] pattern = orientedPattern(sp.pattern());
        tasks.add(() -> {
            World w = world;
            w.p = p;
            if (resetWorld) {
                w.clear();
                w.place(pattern, w.n / 2, w.n / 2);
                resetHistory();
            }
        });
        updateRuleLabel(p);
    }

    private void loadSpecimen(Specimen sp) {
        Params p = sp.params.copy();
        setSliders(p);
        blurb.setText(sp.notes);
        double[][] pattern = orientedPattern(sp.cells);
        tasks.add(() -> {
            World w = world;
            w.p = p;
            w.clear();
            w.place(pattern, w.n / 2, w.n / 2);
            resetHistory();
        });
        updateRuleLabel(p);
    }

    private void setSliders(Params p) {
        suppress = true;
        muSlider.setValue(p.m); sigmaSlider.setValue(p.s); tSlider.setValue(p.T); rSlider.setValue(p.R);
        suppress = false;
    }

    private void updateRuleLabel(Params p) {
        String[] kernels = {"polynomial", "exponential", "step", "staircase"};
        String[] growths = {"polynomial", "Gaussian", "step"};
        ruleLabel.setText(String.format("<html>Kernel: %s rings b = [%s]<br>Growth curve: %s</html>",
                kernels[p.kn - 1], bToString(p.b), growths[p.gn - 1]));
    }

    /** Change one parameter of the rule, leaving the others exactly as they are. */
    private void pushParam(java.util.function.Consumer<Params> change) {
        if (suppress) return;
        tasks.add(() -> {
            Params p = world.p.copy();
            change.accept(p);
            world.p = p;
        });
    }

    private void changeGridSize(int n) {
        tasks.add(() -> {
            World old = world;
            if (old.n == n) return;
            World w = new World(n);
            w.p = old.p.copy();
            // Copy the old world into the centre of the new one
            int on = old.n;
            for (int y = 0; y < Math.min(on, n); y++)
                for (int x = 0; x < Math.min(on, n); x++) {
                    int sx = on <= n ? x : x + (on - n) / 2, sy = on <= n ? y : y + (on - n) / 2;
                    int dx = on <= n ? x + (n - on) / 2 : x, dy = on <= n ? y + (n - on) / 2 : y;
                    w.A[dy * n + dx] = old.A[sy * on + sx];
                }
            world = w;
            resetHistory();
        });
    }

    private void recordSpecimen() {
        World w = world;
        double[] c = w.centroid();
        double[][] cells = w.cropAround(c);
        if (cells == null) { JOptionPane.showMessageDialog(this, "The world is empty: there's nothing to record."); return; }
        if (cells.length > w.n * 0.8 || cells[0].length > w.n * 0.8) {
            JOptionPane.showMessageDialog(this, "The living region fills most of the world. Clear away everything except one creature (right-drag to erase), then record it.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Name this specimen:", "Specimen " + (notebook.size() + 1));
        if (name == null || name.isBlank()) return;
        Params p = w.p.copy();
        String notes = String.format(Locale.ROOT, "Recorded at t = %.1f. Mass %.0f, size %d × %d, speed %.2f cells per unit time. μ = %.4f, σ = %.4f, R = %.0f, T = %.0f.",
                w.time, w.mass(), cells[0].length, cells.length, speed, p.m, p.s, p.R, p.T);
        notebook.addElement(new Specimen(name.trim(), notes, p, cells));
    }

    private void exportNotebook() {
        if (notebook.isEmpty()) { JOptionPane.showMessageDialog(this, "The notebook is empty. Record a specimen first."); return; }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("lenia-notebook.json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < notebook.size(); i++) sb.append("  ").append(notebook.get(i).toJson()).append(i < notebook.size() - 1 ? ",\n" : "\n");
        sb.append("]\n");
        try { Files.writeString(fc.getSelectedFile().toPath(), sb.toString(), StandardCharsets.UTF_8); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage()); }
    }

    /** Load a notebook, or Bert Chan's full animals.json catalogue. */
    @SuppressWarnings("unchecked")
    private void importSpecies() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int added = 0;
        try {
            Object root = Json.parse(Files.readString(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8));
            if (!(root instanceof List)) throw new IllegalArgumentException("expected a list of species");
            for (Object o : (List<Object>) root) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                if (!m.containsKey("params") || !m.containsKey("cells")) continue;
                Map<String, Object> pm = (Map<String, Object>) m.get("params");
                Params p = new Params();
                p.R = ((Number) pm.get("R")).doubleValue();
                p.T = ((Number) pm.get("T")).doubleValue();
                p.b = parseB(String.valueOf(pm.get("b")));
                p.m = ((Number) pm.get("m")).doubleValue();
                p.s = ((Number) pm.get("s")).doubleValue();
                p.kn = pm.containsKey("kn") ? ((Number) pm.get("kn")).intValue() : 1;
                p.gn = pm.containsKey("gn") ? ((Number) pm.get("gn")).intValue() : 1;
                if (p.b.length == 0 || p.kn < 1 || p.kn > 4 || p.gn < 1 || p.gn > 3) continue;
                String cellsSt = String.valueOf(m.get("cells"));
                if (cellsSt.contains("%")) continue;          // 3-D species: not supported
                double[][] cells = decodeRLE(cellsSt);
                if (cells.length == 0) continue;
                String name = String.valueOf(m.getOrDefault("name", "Unnamed"));
                String notes = m.containsKey("notes") ? String.valueOf(m.get("notes"))
                        : "Imported from " + fc.getSelectedFile().getName() + (m.containsKey("code") ? " (code " + m.get("code") + ")" : "") + ".";
                notebook.addElement(new Specimen(name, notes, p, cells));
                added++;
            }
            JOptionPane.showMessageDialog(this, "Added " + added + " species to the notebook.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read that file: " + ex.getMessage());
        }
    }

    private void savePng() {
        BufferedImage img = frame;
        if (img == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("lenia.png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) f = new File(f.getPath() + ".png");
        BufferedImage out = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, 1024, 1024, null);
        g.dispose();
        try { ImageIO.write(out, "png", f); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage()); }
    }

    private void showAbout() {
        String html = """
            <html><body style='width:480px; font-family:sans-serif'>
            <h2>Lenia</h2>
            <p>Lenia was created by Bert Wang-Chak Chan. It's Conway's Game of Life with
            everything made smooth: each cell holds a value between 0 and 1, it senses a soft
            <b>ring</b> of neighbours instead of just eight, and time flows in small steps.</p>
            <p>Each step, every cell takes a weighted average of its surroundings using the ring
            (the <b>kernel</b>, of radius <b>R</b>). That average goes through a bell-shaped
            <b>growth curve</b>: if it's close to the sweet spot <b>μ</b> (within about <b>σ</b>),
            the cell grows; otherwise it fades. <b>T</b> sets how finely time is sliced: each
            step adds 1/T of the growth.</p>
            <p>With the right numbers, the world grows <b>creatures</b>: smooth, self-organising
            patterns that keep their shape and glide, spin, wobble or sit still. Chan studied
            them like a naturalist and catalogued hundreds of species. The 20 in the gallery come
            from his catalogue.</p>
            <p><b>Things to try</b></p>
            <ul>
            <li>Right-drag across a creature to cut it. Small wounds often heal.</li>
            <li>Nudge μ or σ while a creature moves. Many species only live in a narrow band.</li>
            <li>Place two gliders on a collision course.</li>
            <li>Press <b>Random soup</b> with Orbium's rule and look for creatures emerging.</li>
            <li>Record anything new in the <b>notebook</b>. Exports use Chan's own format, and
            <b>Import</b> can load his full catalogue (animals.json from his GitHub repository).</li>
            </ul>
            <p style='color:#666'>Species data: Bert Chan's Lenia catalogue (MIT licence).<br>
            Architect: Mario Gianota · Implementation: Claude (Anthropic) · MIT licence</p>
            </body></html>""";
        JOptionPane.showMessageDialog(this, new JLabel(html), "About Lenia Lab", JOptionPane.PLAIN_MESSAGE);
    }

    private void updateStatus() {
        World w = world;
        Params p = w.p;
        status.setText(String.format(Locale.ROOT, "t = %8.1f   step %,8d   grid %d×%d   %5.0f steps/s   mass %8.1f   speed %5.2f cells/time   μ=%.4f σ=%.4f R=%.0f T=%.0f",
                w.time, w.gen, w.n, w.n, stepsPerSecond, lastMass, speed, p.m, p.s, p.R, p.T));
    }

    // =====================================================================
    //  UI
    // =====================================================================

    private void buildUI() {
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(new EmptyBorder(8, 8, 8, 14));

        JPanel spec = section("Species");
        speciesBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        speciesBox.setAlignmentX(LEFT_ALIGNMENT);
        speciesBox.addActionListener(e -> loadSpecies((Species) speciesBox.getSelectedItem(), true));
        spec.add(speciesBox);
        blurb.setLineWrap(true); blurb.setWrapStyleWord(true); blurb.setEditable(false); blurb.setOpaque(false);
        blurb.setFont(blurb.getFont().deriveFont(12f));
        blurb.setBorder(new EmptyBorder(4, 2, 4, 2));
        blurb.setAlignmentX(LEFT_ALIGNMENT);
        spec.add(blurb);
        JPanel orient = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        orient.add(new JLabel("Orientation: "));
        rotBox.addActionListener(e -> rotation = rotBox.getSelectedIndex());
        orient.add(rotBox);
        orient.add(Box.createHorizontalStrut(8));
        flipBox.addActionListener(e -> flipped = flipBox.isSelected());
        orient.add(flipBox);
        orient.setAlignmentX(LEFT_ALIGNMENT);
        spec.add(orient);
        JPanel specButtons = new JPanel(new GridLayout(1, 2, 4, 4));
        JButton resetSpecies = new JButton("Reset with this");
        resetSpecies.addActionListener(e -> loadSpecies((Species) speciesBox.getSelectedItem(), true));
        placeButton.setToolTipText("Click in the world to add another of the selected species");
        placeButton.addActionListener(e -> placeMode = placeButton.isSelected());
        specButtons.add(resetSpecies);
        specButtons.add(placeButton);
        specButtons.setAlignmentX(LEFT_ALIGNMENT);
        spec.add(specButtons);
        controls.add(spec);

        JPanel rule = section("Rule");
        muSlider.onChange(() -> pushParam(p -> p.m = muSlider.value()));
        sigmaSlider.onChange(() -> pushParam(p -> p.s = sigmaSlider.value()));
        tSlider.onChange(() -> pushParam(p -> p.T = Math.round(tSlider.value())));
        rSlider.onChange(() -> pushParam(p -> p.R = Math.round(rSlider.value())));
        for (ParamSlider ps : new ParamSlider[]{muSlider, sigmaSlider, tSlider, rSlider}) rule.add(ps);
        ruleLabel.setFont(ruleLabel.getFont().deriveFont(11f));
        ruleLabel.setAlignmentX(LEFT_ALIGNMENT);
        rule.add(ruleLabel);
        curves.setAlignmentX(LEFT_ALIGNMENT);
        rule.add(Box.createVerticalStrut(4));
        rule.add(curves);
        controls.add(rule);

        JPanel worldPanel = section("World");
        speedSlider.onChange(() -> stepsPerFrame = (int) Math.round(speedSlider.value()));
        worldPanel.add(speedSlider);
        JPanel wb = new JPanel(new GridLayout(2, 3, 4, 4));
        JButton stepBtn = new JButton("Step");
        JButton clearBtn = new JButton("Clear");
        JButton soupBtn = new JButton("Random soup");
        JButton saveBtn = new JButton("Save PNG");
        JButton aboutBtn = new JButton("About");
        playButton.addActionListener(e -> { running = playButton.isSelected(); playButton.setText(running ? "Pause" : "Play"); });
        stepBtn.addActionListener(e -> singleStep = true);
        clearBtn.addActionListener(e -> tasks.add(() -> { world.clear(); resetHistory(); }));
        soupBtn.addActionListener(e -> tasks.add(() -> { world.randomSoup(rng); resetHistory(); }));
        saveBtn.addActionListener(e -> savePng());
        aboutBtn.addActionListener(e -> showAbout());
        wb.add(playButton); wb.add(stepBtn); wb.add(clearBtn);
        wb.add(soupBtn); wb.add(saveBtn); wb.add(aboutBtn);
        wb.setAlignmentX(LEFT_ALIGNMENT);
        worldPanel.add(Box.createVerticalStrut(4));
        worldPanel.add(wb);
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        opts.add(new JLabel("Grid: "));
        sizeBox.addActionListener(e -> changeGridSize((Integer) sizeBox.getSelectedItem()));
        opts.add(sizeBox);
        opts.add(Box.createHorizontalStrut(10));
        opts.add(new JLabel("Colours: "));
        mapBox.addActionListener(e -> colourMap = (ColourMap) mapBox.getSelectedItem());
        opts.add(mapBox);
        opts.setAlignmentX(LEFT_ALIGNMENT);
        worldPanel.add(opts);
        JLabel ml = new JLabel("Total mass over time");
        ml.setFont(ml.getFont().deriveFont(11f));
        ml.setAlignmentX(LEFT_ALIGNMENT);
        worldPanel.add(ml);
        massPlot.setAlignmentX(LEFT_ALIGNMENT);
        worldPanel.add(massPlot);
        controls.add(worldPanel);

        JPanel nb = section("Naturalist's notebook");
        JList<Specimen> list = new JList<>(notebook);
        list.setVisibleRowCount(5);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Specimen s = list.getSelectedValue();
                if (s != null && e.getClickCount() >= 1) loadSpecimen(s);
            }
        });
        JScrollPane ls = new JScrollPane(list);
        ls.setPreferredSize(new Dimension(300, 100));
        ls.setAlignmentX(LEFT_ALIGNMENT);
        nb.add(ls);
        JPanel nbb = new JPanel(new GridLayout(1, 3, 4, 4));
        JButton rec = new JButton("Record");
        rec.setToolTipText("Save the creature in the world, with the current rule, as a new specimen");
        JButton exp = new JButton("Export…");
        JButton imp = new JButton("Import…");
        imp.setToolTipText("Load a notebook, or Bert Chan's full animals.json catalogue");
        rec.addActionListener(e -> recordSpecimen());
        exp.addActionListener(e -> exportNotebook());
        imp.addActionListener(e -> importSpecies());
        nbb.add(rec); nbb.add(exp); nbb.add(imp);
        nbb.setAlignmentX(LEFT_ALIGNMENT);
        nb.add(Box.createVerticalStrut(4));
        nb.add(nbb);
        controls.add(nb);

        JLabel hint = new JLabel("<html><i>Left-drag: add life · Right-drag: cut / erase</i></html>");
        hint.setBorder(new EmptyBorder(6, 4, 0, 0));
        hint.setAlignmentX(LEFT_ALIGNMENT);
        controls.add(hint);
        controls.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(390, 820));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        status.setBorder(new EmptyBorder(4, 10, 4, 10));
        status.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.WEST);
        getContentPane().add(view, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP),
                new EmptyBorder(4, 6, 6, 6)));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    // =====================================================================
    //  Widgets
    // =====================================================================

    static final class ParamSlider extends JPanel {
        private static final int TICKS = 100000;
        private final double min, max;
        private final String fmt;
        private final JSlider slider = new JSlider(0, TICKS);
        private final JLabel valueLabel = new JLabel();
        private Runnable listener = () -> { };

        ParamSlider(String name, double min, double max, double initial, String fmt) {
            this.min = min; this.max = max; this.fmt = fmt;
            setLayout(new BorderLayout());
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            JPanel top = new JPanel(new BorderLayout());
            JLabel nameLabel = new JLabel(name);
            nameLabel.setFont(nameLabel.getFont().deriveFont(12f));
            valueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            top.add(nameLabel, BorderLayout.WEST);
            top.add(valueLabel, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);
            add(slider, BorderLayout.CENTER);
            slider.addChangeListener(e -> { refresh(); listener.run(); });
            setValue(initial);
        }
        double value() { return min + (max - min) * slider.getValue() / TICKS; }
        void setValue(double v) { slider.setValue((int) Math.round((Math.max(min, Math.min(max, v)) - min) / (max - min) * TICKS)); refresh(); }
        void onChange(Runnable r) { listener = r; }
        private void refresh() { valueLabel.setText(String.format(Locale.ROOT, fmt, value())); }
    }

    final class ViewPanel extends JPanel {
        ViewPanel() {
            setPreferredSize(new Dimension(800, 800));
            setBackground(new Color(0x0b, 0x0b, 0x14));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { handle(e, true); }
                @Override public void mouseDragged(MouseEvent e) { handle(e, false); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }

        private int side() { return Math.min(getWidth(), getHeight()) - 16; }

        private void handle(MouseEvent e, boolean press) {
            int side = side();
            int ox = (getWidth() - side) / 2, oy = (getHeight() - side) / 2;
            World w = world;
            double gx = (e.getX() - ox) * (double) w.n / side, gy = (e.getY() - oy) * (double) w.n / side;
            if (gx < 0 || gy < 0 || gx >= w.n || gy >= w.n) return;
            if (placeMode && !SwingUtilities.isRightMouseButton(e)) {
                if (!press) return;
                Species sp = (Species) speciesBox.getSelectedItem();
                double[][] pat = orientedPattern(sp.pattern());
                tasks.add(() -> { if (world == w) w.place(pat, (int) gx, (int) gy); });
                return;
            }
            boolean add = !SwingUtilities.isRightMouseButton(e);
            double radius = add ? Math.max(3, w.p.R * 0.4) : Math.max(2, w.p.R * 0.25);
            tasks.add(() -> { if (world == w) w.brush(gx, gy, radius, add, rng); });
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            BufferedImage img = frame;
            if (img == null) return;
            int side = side();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, (getWidth() - side) / 2, (getHeight() - side) / 2, side, side, null);
        }
    }

    /** The kernel's radial profile and the growth curve, with where the creature's cells currently sit. */
    final class CurvesPanel extends JPanel {
        CurvesPanel() {
            setPreferredSize(new Dimension(300, 120));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
            setBackground(new Color(0x16, 0x16, 0x22));
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Params p = world.p;
            int w = getWidth(), h = getHeight(), half = w / 2 - 6;
            g.setFont(g.getFont().deriveFont(10f));

            // Left: kernel shell K(r), r from 0 to R
            int x0 = 6, y0 = h - 16, kh = h - 32;
            double kmax = 1e-9;
            for (int i = 0; i <= 100; i++) kmax = Math.max(kmax, World.shell(p, i / 100.0));
            Path2D path = new Path2D.Double();
            for (int i = 0; i <= 100; i++) {
                double x = x0 + i / 100.0 * (half - 6), y = y0 - World.shell(p, i / 100.0) / kmax * kh;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            g.setColor(new Color(120, 190, 255));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(path);
            g.setColor(new Color(170, 170, 190));
            g.drawString("kernel: distance 0 → R", x0, h - 3);

            // Right: growth G(u), u from 0 to 0.5, plus histogram of current potentials
            int gx0 = w / 2 + 2, gw = half - 2;
            double[] hist;
            synchronized (potentialHist) { hist = potentialHist.clone(); }
            double hmax = 1e-9;
            for (double v : hist) hmax = Math.max(hmax, v);
            g.setColor(new Color(255, 190, 120, 70));
            for (int i = 0; i < hist.length; i++) {
                int bx = gx0 + (int) (i / (double) hist.length * gw), bw = Math.max(1, gw / hist.length);
                int bh = (int) (hist[i] / hmax * kh);
                g.fillRect(bx, y0 - bh, bw, bh);
            }
            g.setColor(new Color(255, 255, 255, 40));
            g.drawLine(gx0, y0 - kh / 2, gx0 + gw, y0 - kh / 2);
            Path2D gp = new Path2D.Double();
            for (int i = 0; i <= 200; i++) {
                double u = 0.5 * i / 200.0;
                double x = gx0 + i / 200.0 * gw, y = y0 - (World.growth(p.gn, u, p.m, p.s) + 1) / 2 * kh;
                if (i == 0) gp.moveTo(x, y); else gp.lineTo(x, y);
            }
            g.setColor(new Color(255, 140, 170));
            g.draw(gp);
            g.setColor(new Color(170, 170, 190));
            g.drawString("growth: potential 0 → 0.5", gx0, h - 3);
            g.drawString("grow", gx0 + gw - 26, 12);
            g.drawString("fade", gx0 + gw - 26, y0 - 2);
        }
    }

    final class MassPlot extends JPanel {
        MassPlot() {
            setPreferredSize(new Dimension(300, 70));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            setBackground(new Color(0x16, 0x16, 0x22));
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double[] d;
            synchronized (histLock) {
                d = new double[massCount];
                int start = (massHead - massCount + massHistory.length) % massHistory.length;
                for (int i = 0; i < massCount; i++) d[i] = massHistory[(start + i) % massHistory.length];
            }
            int w = getWidth(), h = getHeight();
            if (d.length < 2) { g.setColor(Color.GRAY); g.drawString("waiting…", 8, h / 2); return; }
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double v : d) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (hi - lo < Math.max(1, hi * 0.05)) { double mid = (hi + lo) / 2, span = Math.max(1, hi * 0.05); lo = mid - span; hi = mid + span; }
            g.setColor(new Color(150, 230, 190));
            g.setStroke(new BasicStroke(1.5f));
            int px = -1, py = -1;
            for (int i = 0; i < d.length; i++) {
                int x = (int) ((double) i / (d.length - 1) * (w - 1)), y = (int) (h - 4 - (d[i] - lo) / (hi - lo) * (h - 12));
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x; py = y;
            }
            g.setColor(new Color(210, 210, 210));
            g.setFont(g.getFont().deriveFont(10f));
            g.drawString(String.format("%.0f", d[d.length - 1]), w - 50, 12);
        }
    }
}
