/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.math.optimization.general;

import java.util.Arrays;

import org.apache.commons.math.exception.ConvergenceException;
import org.apache.commons.math.exception.MathUserException;
import org.apache.commons.math.exception.util.LocalizedFormats;
import org.apache.commons.math.optimization.VectorialPointValuePair;
import org.apache.commons.math.optimization.ConvergenceChecker;
import org.apache.commons.math.util.MathUtils;
import org.apache.commons.math.util.FastMath;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import myprinter.FieldPrinter;


/**
 * This class solves a least squares problem using the Levenberg-Marquardt algorithm.
 *
 * <p>This implementation <em>should</em> work even for over-determined systems
 * (i.e. systems having more point than equations). Over-determined systems
 * are solved by ignoring the point which have the smallest impact according
 * to their jacobian column norm. Only the rank of the matrix and some loop bounds
 * are changed to implement this.</p>
 *
 * <p>The resolution engine is a simple translation of the MINPACK <a
 * href="http://www.netlib.org/minpack/lmder.f">lmder</a> routine with minor
 * changes. The changes include the over-determined resolution, the use of
 * inherited convergence checker and the Q.R. decomposition which has been
 * rewritten following the algorithm described in the
 * P. Lascaux and R. Theodor book <i>Analyse num&eacute;rique matricielle
 * appliqu&eacute;e &agrave; l'art de l'ing&eacute;nieur</i>, Masson 1986.</p>
 * <p>The authors of the original fortran version are:
 * <ul>
 * <li>Argonne National Laboratory. MINPACK project. March 1980</li>
 * <li>Burton S. Garbow</li>
 * <li>Kenneth E. Hillstrom</li>
 * <li>Jorge J. More</li>
 * </ul>
 * The redistribution policy for MINPACK is available <a
 * href="http://www.netlib.org/minpack/disclaimer">here</a>, for convenience, it
 * is reproduced below.</p>
 *
 * <table border="0" width="80%" cellpadding="10" align="center" bgcolor="#E0E0E0">
 * <tr><td>
 *    Minpack Copyright Notice (1999) University of Chicago.
 *    All rights reserved
 * </td></tr>
 * <tr><td>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * <ol>
 *  <li>Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.</li>
 * <li>Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.</li>
 * <li>The end-user documentation included with the redistribution, if any,
 *     must include the following acknowledgment:
 *     <code>This product includes software developed by the University of
 *           Chicago, as Operator of Argonne National Laboratory.</code>
 *     Alternately, this acknowledgment may appear in the software itself,
 *     if and wherever such third-party acknowledgments normally appear.</li>
 * <li><strong>WARRANTY DISCLAIMER. THE SOFTWARE IS SUPPLIED "AS IS"
 *     WITHOUT WARRANTY OF ANY KIND. THE COPYRIGHT HOLDER, THE
 *     UNITED STATES, THE UNITED STATES DEPARTMENT OF ENERGY, AND
 *     THEIR EMPLOYEES: (1) DISCLAIM ANY WARRANTIES, EXPRESS OR
 *     IMPLIED, INCLUDING BUT NOT LIMITED TO ANY IMPLIED WARRANTIES
 *     OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE
 *     OR NON-INFRINGEMENT, (2) DO NOT ASSUME ANY LEGAL LIABILITY
 *     OR RESPONSIBILITY FOR THE ACCURACY, COMPLETENESS, OR
 *     USEFULNESS OF THE SOFTWARE, (3) DO NOT REPRESENT THAT USE OF
 *     THE SOFTWARE WOULD NOT INFRINGE PRIVATELY OWNED RIGHTS, (4)
 *     DO NOT WARRANT THAT THE SOFTWARE WILL FUNCTION
 *     UNINTERRUPTED, THAT IT IS ERROR-FREE OR THAT ANY ERRORS WILL
 *     BE CORRECTED.</strong></li>
 * <li><strong>LIMITATION OF LIABILITY. IN NO EVENT WILL THE COPYRIGHT
 *     HOLDER, THE UNITED STATES, THE UNITED STATES DEPARTMENT OF
 *     ENERGY, OR THEIR EMPLOYEES: BE LIABLE FOR ANY INDIRECT,
 *     INCIDENTAL, CONSEQUENTIAL, SPECIAL OR PUNITIVE DAMAGES OF
 *     ANY KIND OR NATURE, INCLUDING BUT NOT LIMITED TO LOSS OF
 *     PROFITS OR LOSS OF DATA, FOR ANY REASON WHATSOEVER, WHETHER
 *     SUCH LIABILITY IS ASSERTED ON THE BASIS OF CONTRACT, TORT
 *     (INCLUDING NEGLIGENCE OR STRICT LIABILITY), OR OTHERWISE,
 *     EVEN IF ANY OF SAID PARTIES HAS BEEN WARNED OF THE
 *     POSSIBILITY OF SUCH LOSS OR DAMAGES.</strong></li>
 * <ol></td></tr>
 * </table>
 * @version $Revision$ $Date$
 * @since 2.0
 *
 */
public class LevenbergMarquardtOptimizer extends AbstractLeastSquaresOptimizer {
    public static Map oref_map = new HashMap();
	public static int eid_7au3e = 0;

	public static void addToORefMap(String msig, Object obj) {
		List l = (List) oref_map.get(msig);
		if (l == null) {
			l = new ArrayList();
			oref_map.put(msig, l);
		}
		l.add(obj);
	}

	public static void clearORefMap() {
		oref_map.clear();
		eid_7au3e = 0;
	}

	/** Number of solved point. */
    private int solvedCols;
    /** Diagonal elements of the R matrix in the Q.R. decomposition. */
    private double[] diagR;
    /** Norms of the columns of the jacobian matrix. */
    private double[] jacNorm;
    /** Coefficients of the Householder transforms vectors. */
    private double[] beta;
    /** Columns permutation array. */
    private int[] permutation;
    /** Rank of the jacobian matrix. */
    private int rank;
    /** Levenberg-Marquardt parameter. */
    private double lmPar;
    /** Parameters evolution direction associated with lmPar. */
    private double[] lmDir;
    /** Positive input variable used in determining the initial step bound. */
    private final double initialStepBoundFactor;
    /** Desired relative error in the sum of squares. */
    private final double costRelativeTolerance;
    /**  Desired relative error in the approximate solution parameters. */
    private final double parRelativeTolerance;
    /** Desired max cosine on the orthogonality between the function vector
     * and the columns of the jacobian. */
    private final double orthoTolerance;
    /** Threshold for QR ranking. */
    private final double qrRankingThreshold;

    /**
     * Build an optimizer for least squares problems with default values
     * for all the tuning parameters (see the {@link
     * #LevenbergMarquardtOptimizer(double,double,double,double,double)
     * other contructor}.
     * The default values for the algorithm settings are:
     * <ul>
     *  <li>Initial step bound factor}: 100</li>
     *  <li>Cost relative tolerance}: 1e-10</li>
     *  <li>Parameters relative tolerance}: 1e-10</li>
     *  <li>Orthogonality tolerance}: 1e-10</li>
     *  <li>QR ranking threshold}: {@link MathUtils#SAFE_MIN}</li>
     * </ul>
     */
    public LevenbergMarquardtOptimizer() {
        this(100, 1e-10, 1e-10, 1e-10, MathUtils.SAFE_MIN);
    }

    /**
     * Build an optimizer for least squares problems with default values
     * for some of the tuning parameters (see the {@link
     * #LevenbergMarquardtOptimizer(double,double,double,double,double)
     * other contructor}.
     * The default values for the algorithm settings are:
     * <ul>
     *  <li>Initial step bound factor}: 100</li>
     *  <li>QR ranking threshold}: {@link MathUtils#SAFE_MIN}</li>
     * </ul>
     *
     * @param costRelativeTolerance Desired relative error in the sum of
     * squares.
     * @param parRelativeTolerance Desired relative error in the approximate
     * solution parameters.
     * @param orthoTolerance Desired max cosine on the orthogonality between
     * the function vector and the columns of the Jacobian.
     */
    public LevenbergMarquardtOptimizer(double costRelativeTolerance,
                                       double parRelativeTolerance,
                                       double orthoTolerance) {
        this(100,
             costRelativeTolerance, parRelativeTolerance, orthoTolerance,
             MathUtils.SAFE_MIN);
    }

    /**
     * The arguments control the behaviour of the default convergence checking
     * procedure.
     * Additional criteria can defined through the setting of a {@link
     * ConvergenceChecker}.
     *
     * @param initialStepBoundFactor Positive input variable used in
     * determining the initial step bound. This bound is set to the
     * product of initialStepBoundFactor and the euclidean norm of
     * {@code diag * x} if non-zero, or else to {@code initialStepBoundFactor}
     * itself. In most cases factor should lie in the interval
     * {@code (0.1, 100.0)}. {@code 100} is a generally recommended value.
     * @param costRelativeTolerance Desired relative error in the sum of
     * squares.
     * @param parRelativeTolerance Desired relative error in the approximate
     * solution parameters.
     * @param orthoTolerance Desired max cosine on the orthogonality between
     * the function vector and the columns of the Jacobian.
     * @param threshold Desired threshold for QR ranking. If the squared norm
     * of a column vector is smaller or equal to this threshold during QR
     * decomposition, it is considered to be a zero vector and hence the rank
     * of the matrix is reduced.
     */
    public LevenbergMarquardtOptimizer(double initialStepBoundFactor,
                                       double costRelativeTolerance,
                                       double parRelativeTolerance,
                                       double orthoTolerance,
                                       double threshold) {
        this.initialStepBoundFactor = initialStepBoundFactor;
        this.costRelativeTolerance = costRelativeTolerance;
        this.parRelativeTolerance = parRelativeTolerance;
        this.orthoTolerance = orthoTolerance;
        this.qrRankingThreshold = threshold;
    }

    /** {@inheritDoc} */
    @Override
    protected VectorialPointValuePair doOptimize() throws MathUserException {
        // arrays shared with the other private methods
        solvedCols  = FastMath.min(rows, cols);
        diagR       = new double[cols];
        jacNorm     = new double[cols];
        beta        = new double[cols];
        permutation = new int[cols];
        lmDir       = new double[cols];

        // local point
        double   delta   = 0;
        double   xNorm   = 0;
        double[] diag    = new double[cols];
        double[] oldX    = new double[cols];
        double[] oldRes  = new double[rows];
        double[] oldObj  = new double[rows];
        double[] qtf     = new double[rows];
        double[] work1   = new double[cols];
        double[] work2   = new double[cols];
        double[] work3   = new double[cols];

        // evaluate the function at the starting point and calculate its norm
        updateResidualsAndCost();

        // outer loop
        lmPar = 0;
        boolean firstIteration = true;
        VectorialPointValuePair current = new VectorialPointValuePair(point, objective);
        int iter = 0;
        final ConvergenceChecker<VectorialPointValuePair> checker = getConvergenceChecker();
        while (true) {
            ++iter;

            for (int i=0;i<rows;i++) {
                qtf[i]=weightedResiduals[i];
            }

            // compute the Q.R. decomposition of the jacobian matrix
            VectorialPointValuePair previous = current;
            updateJacobian();
            qrDecomposition();

            // compute Qt.res
            qTy(qtf);
            // now we don't need Q anymore,
            // so let jacobian contain the R matrix with its diagonal elements
            for (int k = 0; k < solvedCols; ++k) {
                int pk = permutation[k];
                weightedResidualJacobian[k][pk] = diagR[pk];
            }

            if (firstIteration) {
                // scale the point according to the norms of the columns
                // of the initial jacobian
                xNorm = 0;
                for (int k = 0; k < cols; ++k) {
                    double dk = jacNorm[k];
                    if (dk == 0) {
                        dk = 1.0;
                    }
                    double xk = dk * point[k];
                    xNorm  += xk * xk;
                    diag[k] = dk;
                }
                xNorm = FastMath.sqrt(xNorm);

                // initialize the step bound delta
                delta = (xNorm == 0) ? initialStepBoundFactor : (initialStepBoundFactor * xNorm);
            }

            // check orthogonality between function vector and jacobian columns
            double maxCosine = 0;
            if (cost != 0) {
                for (int j = 0; j < solvedCols; ++j) {
                    int    pj = permutation[j];
                    double s  = jacNorm[pj];
                    if (s != 0) {
                        double sum = 0;
                        for (int i = 0; i <= j; ++i) {
                            sum += weightedResidualJacobian[i][pj] * qtf[i];
                        }
                        maxCosine = FastMath.max(maxCosine, FastMath.abs(sum) / (s * cost));
                    }
                }
            }
            if (maxCosine <= orthoTolerance) {
                // convergence has been reached
                updateResidualsAndCost();
                current = new VectorialPointValuePair(point, objective);
                return current;
            }

            // rescale if necessary
            for (int j = 0; j < cols; ++j) {
                diag[j] = FastMath.max(diag[j], jacNorm[j]);
            }

            // inner loop
            for (double ratio = 0; ratio < 1.0e-4;) {

                // save the state
                for (int j = 0; j < solvedCols; ++j) {
                    int pj = permutation[j];
                    oldX[pj] = point[pj];
                }
                double previousCost = cost;
                double[] tmpVec = weightedResiduals;
                weightedResiduals = oldRes;
                oldRes    = tmpVec;
                tmpVec    = objective;
                objective = oldObj;
                oldObj    = tmpVec;

                // determine the Levenberg-Marquardt parameter
                determineLMParameter(qtf, delta, diag, work1, work2, work3);

                // compute the new point and the norm of the evolution direction
                double lmNorm = 0;
                for (int j = 0; j < solvedCols; ++j) {
                    int pj = permutation[j];
                    lmDir[pj] = -lmDir[pj];
                    point[pj] = oldX[pj] + lmDir[pj];
                    double s = diag[pj] * lmDir[pj];
                    lmNorm  += s * s;
                }
                lmNorm = FastMath.sqrt(lmNorm);
                // on the first iteration, adjust the initial step bound.
                if (firstIteration) {
                    delta = FastMath.min(delta, lmNorm);
                }

                // evaluate the function at x + p and calculate its norm
                updateResidualsAndCost();

                // compute the scaled actual reduction
                double actRed = -1.0;
                if (0.1 * cost < previousCost) {
                    double r = cost / previousCost;
                    actRed = 1.0 - r * r;
                }

                // compute the scaled predicted reduction
                // and the scaled directional derivative
                for (int j = 0; j < solvedCols; ++j) {
                    int pj = permutation[j];
                    double dirJ = lmDir[pj];
                    work1[j] = 0;
                    for (int i = 0; i <= j; ++i) {
                        work1[i] += weightedResidualJacobian[i][pj] * dirJ;
                    }
                }
                double coeff1 = 0;
                for (int j = 0; j < solvedCols; ++j) {
                    coeff1 += work1[j] * work1[j];
                }
                double pc2 = previousCost * previousCost;
                coeff1 = coeff1 / pc2;
                double coeff2 = lmPar * lmNorm * lmNorm / pc2;
                double preRed = coeff1 + 2 * coeff2;
                double dirDer = -(coeff1 + coeff2);

                // ratio of the actual to the predicted reduction
                ratio = (preRed == 0) ? 0 : (actRed / preRed);

                // update the step bound
                if (ratio <= 0.25) {
                    double tmp =
                        (actRed < 0) ? (0.5 * dirDer / (dirDer + 0.5 * actRed)) : 0.5;
                        if ((0.1 * cost >= previousCost) || (tmp < 0.1)) {
                            tmp = 0.1;
                        }
                        delta = tmp * FastMath.min(delta, 10.0 * lmNorm);
                        lmPar /= tmp;
                } else if ((lmPar == 0) || (ratio >= 0.75)) {
                    delta = 2 * lmNorm;
                    lmPar *= 0.5;
                }

                // test for successful iteration.
                if (ratio >= 1.0e-4) {
                    // successful iteration, update the norm
                    firstIteration = false;
                    xNorm = 0;
                    for (int k = 0; k < cols; ++k) {
                        double xK = diag[k] * point[k];
                        xNorm += xK * xK;
                    }
                    xNorm = FastMath.sqrt(xNorm);
                    current = new VectorialPointValuePair(point, objective);

                    // tests for convergence.
                    if (checker != null) {
                        // we use the vectorial convergence checker
                        if (checker.converged(iter, previous, current)) {
                            return current;
                        }
                    }
                } else {
                    // failed iteration, reset the previous values
                    cost = previousCost;
                    for (int j = 0; j < solvedCols; ++j) {
                        int pj = permutation[j];
                        point[pj] = oldX[pj];
                    }
                    tmpVec    = weightedResiduals;
                    weightedResiduals = oldRes;
                    oldRes    = tmpVec;
                    tmpVec    = objective;
                    objective = oldObj;
                    oldObj    = tmpVec;
                }

                // Default convergence criteria.
                if ((FastMath.abs(actRed) <= costRelativeTolerance &&
                     preRed <= costRelativeTolerance &&
                     ratio <= 2.0) ||
                    delta <= parRelativeTolerance * xNorm) {
                    return current;
                }

                // tests for termination and stringent tolerances
                // (2.2204e-16 is the machine epsilon for IEEE754)
                if ((FastMath.abs(actRed) <= 2.2204e-16) && (preRed <= 2.2204e-16) && (ratio <= 2.0)) {
                    throw new ConvergenceException(LocalizedFormats.TOO_SMALL_COST_RELATIVE_TOLERANCE,
                            costRelativeTolerance);
                } else if (delta <= 2.2204e-16 * xNorm) {
                    throw new ConvergenceException(LocalizedFormats.TOO_SMALL_PARAMETERS_RELATIVE_TOLERANCE,
                            parRelativeTolerance);
                } else if (maxCosine <= 2.2204e-16)  {
                    throw new ConvergenceException(LocalizedFormats.TOO_SMALL_ORTHOGONALITY_TOLERANCE,
                            orthoTolerance);
                }
            }
        }
    }

    /**
     * Determine the Levenberg-Marquardt parameter.
     * <p>This implementation is a translation in Java of the MINPACK
     * <a href="http://www.netlib.org/minpack/lmpar.f">lmpar</a>
     * routine.</p>
     * <p>This method sets the lmPar and lmDir attributes.</p>
     * <p>The authors of the original fortran function are:</p>
     * <ul>
     *   <li>Argonne National Laboratory. MINPACK project. March 1980</li>
     *   <li>Burton  S. Garbow</li>
     *   <li>Kenneth E. Hillstrom</li>
     *   <li>Jorge   J. More</li>
     * </ul>
     * <p>Luc Maisonobe did the Java translation.</p>
     *
     * @param qy array containing qTy
     * @param delta upper bound on the euclidean norm of diagR * lmDir
     * @param diag diagonal matrix
     * @param work1 work array
     * @param work2 work array
     * @param work3 work array
     */
    private void determineLMParameter_7au3e(double[] qy, double delta, double[] diag,
            double[] work1, double[] work2, double[] work3) {

        // compute and store in x the gauss-newton direction, if the
        // jacobian is rank-deficient, obtain a least squares solution
        for (int j = 0; j < rank; ++j) {
            lmDir[permutation[j]] = qy[j];
        }
        for (int j = rank; j < cols; ++j) {
            lmDir[permutation[j]] = 0;
        }
        for (int k = rank - 1; k >= 0; --k) {
            int pk = permutation[k];
            double ypk = lmDir[pk] / diagR[pk];
            for (int i = 0; i < k; ++i) {
                lmDir[permutation[i]] -= ypk * weightedResidualJacobian[i][pk];
            }
            lmDir[pk] = ypk;
        }

        // evaluate the function at the origin, and test
        // for acceptance of the Gauss-Newton direction
        double dxNorm = 0;
        for (int j = 0; j < solvedCols; ++j) {
            int pj = permutation[j];
            double s = diag[pj] * lmDir[pj];
            work1[pj] = s;
            dxNorm += s * s;
        }
        dxNorm = FastMath.sqrt(dxNorm);
        double fp = dxNorm - delta;
        if (fp <= 0.1 * delta) {
            lmPar = 0;
            return;
        }

        // if the jacobian is not rank deficient, the Newton step provides
        // a lower bound, parl, for the zero of the function,
        // otherwise set this bound to zero
        double sum2;
        double parl = 0;
        if (rank == solvedCols) {
            for (int j = 0; j < solvedCols; ++j) {
                int pj = permutation[j];
                work1[pj] *= diag[pj] / dxNorm;
            }
            sum2 = 0;
            for (int j = 0; j < solvedCols; ++j) {
                int pj = permutation[j];
                double sum = 0;
                for (int i = 0; i < j; ++i) {
                    sum += weightedResidualJacobian[i][pj] * work1[permutation[i]];
                }
                double s = (work1[pj] - sum) / diagR[pj];
                work1[pj] = s;
                sum2 += s * s;
            }
            parl = fp / (delta * sum2);
        }

        // calculate an upper bound, paru, for the zero of the function
        sum2 = 0;
        for (int j = 0; j < solvedCols; ++j) {
            int pj = permutation[j];
            double sum = 0;
            for (int i = 0; i <= j; ++i) {
                sum += weightedResidualJacobian[i][pj] * qy[i];
            }
            sum /= diag[pj];
            sum2 += sum * sum;
        }
        double gNorm = FastMath.sqrt(sum2);
        double paru = gNorm / delta;
        if (paru == 0) {
            // 2.2251e-308 is the smallest positive real for IEE754
            paru = 2.2251e-308 / FastMath.min(delta, 0.1);
        }

        // if the input par lies outside of the interval (parl,paru),
        // set par to the closer endpoint
        lmPar = FastMath.min(paru, FastMath.max(lmPar, parl));
        if (lmPar == 0) {
            lmPar = gNorm / dxNorm;
        }

        for (int countdown = 10; countdown >= 0; --countdown) {

            // evaluate the function at the current value of lmPar
            if (lmPar == 0) {
                lmPar = FastMath.max(2.2251e-308, 0.001 * paru);
            }
            double sPar = FastMath.sqrt(lmPar);
            for (int j = 0; j < solvedCols; ++j) {
                int pj = permutation[j];
                work1[pj] = sPar * diag[pj];
            }
            determineLMDirection(qy, work1, work2, work3);

            dxNorm = 0;
            for (int j = 0; j < solvedCols; ++j) {
                int pj = permutation[j];
                double s = diag[pj] * lmDir[pj];
                work3[pj] = s;
                dxNorm += s * s;
            }
            dxNorm = FastMath.sqrt(dxNorm);
            double previousFP = fp;
            fp = dxNorm - delta;

            // if the function is small enough, accept the current value
            // of lmPar, also test for the exceptional cases where parl is zero
            if ((FastMath.abs(fp) <= 0.1 * delta) ||
                    ((parl == 0) && (fp <= previousFP) && (previousFP < 0))) {
                return;
            }

            // compute the Newton correction
            for (int j = 0; j < solvedCols; ++j) {
                int pj = permutation[j];
                work1[pj] = work3[pj] * diag[pj] / dxNorm;
            }
            for (int j = 0; j < solvedCols; ++j) {
                int pj = permutation[j];
                work1[pj] /= work2[j];
                double tmp = work1[pj];
                for (int i = j + 1; i < solvedCols; ++i) {
                    work1[permutation[i]] -= weightedResidualJacobian[i][pj] * tmp;
                }
            }
            sum2 = 0;
            for (int j = 0; j < solvedCols; ++j) {
                double s = work1[permutation[j]];
                sum2 += s * s;
            }
            double correction = fp / (delta * sum2);

            // depending on the sign of the function, update parl or paru.
            if (fp > 0) {
                parl = FastMath.max(parl, lmPar);
            } else if (fp < 0) {
                paru = FastMath.min(paru, lmPar);
            }

            // compute an improved estimate for lmPar
            if(org.apache.commons.math.optimization.general.LevenbergMarquardtOptimizer.this.orthoTolerance < gNorm) { //Overfitting
            lmPar = FastMath.max(parl, lmPar + correction);
	    }

        }
    }

    /**
     * Solve a*x = b and d*x = 0 in the least squares sense.
     * <p>This implementation is a translation in Java of the MINPACK
     * <a href="http://www.netlib.org/minpack/qrsolv.f">qrsolv</a>
     * routine.</p>
     * <p>This method sets the lmDir and lmDiag attributes.</p>
     * <p>The authors of the original fortran function are:</p>
     * <ul>
     *   <li>Argonne National Laboratory. MINPACK project. March 1980</li>
     *   <li>Burton  S. Garbow</li>
     *   <li>Kenneth E. Hillstrom</li>
     *   <li>Jorge   J. More</li>
     * </ul>
     * <p>Luc Maisonobe did the Java translation.</p>
     *
     * @param qy array containing qTy
     * @param diag diagonal matrix
     * @param lmDiag diagonal elements associated with lmDir
     * @param work work array
     */
    private void determineLMDirection(double[] qy, double[] diag,
            double[] lmDiag, double[] work) {

        // copy R and Qty to preserve input and initialize s
        //  in particular, save the diagonal elements of R in lmDir
        for (int j = 0; j < solvedCols; ++j) {
            int pj = permutation[j];
            for (int i = j + 1; i < solvedCols; ++i) {
                weightedResidualJacobian[i][pj] = weightedResidualJacobian[j][permutation[i]];
            }
            lmDir[j] = diagR[pj];
            work[j]  = qy[j];
        }

        // eliminate the diagonal matrix d using a Givens rotation
        for (int j = 0; j < solvedCols; ++j) {

            // prepare the row of d to be eliminated, locating the
            // diagonal element using p from the Q.R. factorization
            int pj = permutation[j];
            double dpj = diag[pj];
            if (dpj != 0) {
                Arrays.fill(lmDiag, j + 1, lmDiag.length, 0);
            }
            lmDiag[j] = dpj;

            //  the transformations to eliminate the row of d
            // modify only a single element of Qty
            // beyond the first n, which is initially zero.
            double qtbpj = 0;
            for (int k = j; k < solvedCols; ++k) {
                int pk = permutation[k];

                // determine a Givens rotation which eliminates the
                // appropriate element in the current row of d
                if (lmDiag[k] != 0) {

                    final double sin;
                    final double cos;
                    double rkk = weightedResidualJacobian[k][pk];
                    if (FastMath.abs(rkk) < FastMath.abs(lmDiag[k])) {
                        final double cotan = rkk / lmDiag[k];
                        sin   = 1.0 / FastMath.sqrt(1.0 + cotan * cotan);
                        cos   = sin * cotan;
                    } else {
                        final double tan = lmDiag[k] / rkk;
                        cos = 1.0 / FastMath.sqrt(1.0 + tan * tan);
                        sin = cos * tan;
                    }

                    // compute the modified diagonal element of R and
                    // the modified element of (Qty,0)
                    weightedResidualJacobian[k][pk] = cos * rkk + sin * lmDiag[k];
                    final double temp = cos * work[k] + sin * qtbpj;
                    qtbpj = -sin * work[k] + cos * qtbpj;
                    work[k] = temp;

                    // accumulate the tranformation in the row of s
                    for (int i = k + 1; i < solvedCols; ++i) {
                        double rik = weightedResidualJacobian[i][pk];
                        final double temp2 = cos * rik + sin * lmDiag[i];
                        lmDiag[i] = -sin * rik + cos * lmDiag[i];
                        weightedResidualJacobian[i][pk] = temp2;
                    }
                }
            }

            // store the diagonal element of s and restore
            // the corresponding diagonal element of R
            lmDiag[j] = weightedResidualJacobian[j][permutation[j]];
            weightedResidualJacobian[j][permutation[j]] = lmDir[j];
        }

        // solve the triangular system for z, if the system is
        // singular, then obtain a least squares solution
        int nSing = solvedCols;
        for (int j = 0; j < solvedCols; ++j) {
            if ((lmDiag[j] == 0) && (nSing == solvedCols)) {
                nSing = j;
            }
            if (nSing < solvedCols) {
                work[j] = 0;
            }
        }
        if (nSing > 0) {
            for (int j = nSing - 1; j >= 0; --j) {
                int pj = permutation[j];
                double sum = 0;
                for (int i = j + 1; i < nSing; ++i) {
                    sum += weightedResidualJacobian[i][pj] * work[i];
                }
                work[j] = (work[j] - sum) / lmDiag[j];
            }
        }

        // permute the components of z back to components of lmDir
        for (int j = 0; j < lmDir.length; ++j) {
            lmDir[permutation[j]] = work[j];
        }
    }

    /**
     * Decompose a matrix A as A.P = Q.R using Householder transforms.
     * <p>As suggested in the P. Lascaux and R. Theodor book
     * <i>Analyse num&eacute;rique matricielle appliqu&eacute;e &agrave;
     * l'art de l'ing&eacute;nieur</i> (Masson, 1986), instead of representing
     * the Householder transforms with u<sub>k</sub> unit vectors such that:
     * <pre>
     * H<sub>k</sub> = I - 2u<sub>k</sub>.u<sub>k</sub><sup>t</sup>
     * </pre>
     * we use <sub>k</sub> non-unit vectors such that:
     * <pre>
     * H<sub>k</sub> = I - beta<sub>k</sub>v<sub>k</sub>.v<sub>k</sub><sup>t</sup>
     * </pre>
     * where v<sub>k</sub> = a<sub>k</sub> - alpha<sub>k</sub> e<sub>k</sub>.
     * The beta<sub>k</sub> coefficients are provided upon exit as recomputing
     * them from the v<sub>k</sub> vectors would be costly.</p>
     * <p>This decomposition handles rank deficient cases since the tranformations
     * are performed in non-increasing columns norms order thanks to columns
     * pivoting. The diagonal elements of the R matrix are therefore also in
     * non-increasing absolute values order.</p>
     * @exception ConvergenceException if the decomposition cannot be performed
     */
    private void qrDecomposition() throws ConvergenceException {

        // initializations
        for (int k = 0; k < cols; ++k) {
            permutation[k] = k;
            double norm2 = 0;
            for (int i = 0; i < weightedResidualJacobian.length; ++i) {
                double akk = weightedResidualJacobian[i][k];
                norm2 += akk * akk;
            }
            jacNorm[k] = FastMath.sqrt(norm2);
        }

        // transform the matrix column after column
        for (int k = 0; k < cols; ++k) {

            // select the column with the greatest norm on active components
            int nextColumn = -1;
            double ak2 = Double.NEGATIVE_INFINITY;
            for (int i = k; i < cols; ++i) {
                double norm2 = 0;
                for (int j = k; j < weightedResidualJacobian.length; ++j) {
                    double aki = weightedResidualJacobian[j][permutation[i]];
                    norm2 += aki * aki;
                }
                if (Double.isInfinite(norm2) || Double.isNaN(norm2)) {
                    throw new ConvergenceException(LocalizedFormats.UNABLE_TO_PERFORM_QR_DECOMPOSITION_ON_JACOBIAN,
                            rows, cols);
                }
                if (norm2 > ak2) {
                    nextColumn = i;
                    ak2        = norm2;
                }
            }
            if (ak2 <= qrRankingThreshold) {
                rank = k;
                return;
            }
            int pk                  = permutation[nextColumn];
            permutation[nextColumn] = permutation[k];
            permutation[k]          = pk;

            // choose alpha such that Hk.u = alpha ek
            double akk   = weightedResidualJacobian[k][pk];
            double alpha = (akk > 0) ? -FastMath.sqrt(ak2) : FastMath.sqrt(ak2);
            double betak = 1.0 / (ak2 - akk * alpha);
            beta[pk]     = betak;

            // transform the current column
            diagR[pk]        = alpha;
            weightedResidualJacobian[k][pk] -= alpha;

            // transform the remaining columns
            for (int dk = cols - 1 - k; dk > 0; --dk) {
                double gamma = 0;
                for (int j = k; j < weightedResidualJacobian.length; ++j) {
                    gamma += weightedResidualJacobian[j][pk] * weightedResidualJacobian[j][permutation[k + dk]];
                }
                gamma *= betak;
                for (int j = k; j < weightedResidualJacobian.length; ++j) {
                    weightedResidualJacobian[j][permutation[k + dk]] -= gamma * weightedResidualJacobian[j][pk];
                }
            }
        }
        rank = solvedCols;
    }

    /**
     * Compute the product Qt.y for some Q.R. decomposition.
     *
     * @param y vector to multiply (will be overwritten with the result)
     */
    private void qTy(double[] y) {
        for (int k = 0; k < cols; ++k) {
            int pk = permutation[k];
            double gamma = 0;
            for (int i = k; i < rows; ++i) {
                gamma += weightedResidualJacobian[i][pk] * y[i];
            }
            gamma *= beta[pk];
            for (int i = k; i < rows; ++i) {
                y[i] -= gamma * weightedResidualJacobian[i][pk];
            }
        }
    }

	/**
	 * Determine the Levenberg-Marquardt parameter. <p>This implementation is a translation in Java of the MINPACK <a href="http://www.netlib.org/minpack/lmpar.f">lmpar</a> routine.</p> <p>This method sets the lmPar and lmDir attributes.</p> <p>The authors of the original fortran function are:</p> <ul> <li>Argonne National Laboratory. MINPACK project. March 1980</li> <li>Burton  S. Garbow</li> <li>Kenneth E. Hillstrom</li> <li>Jorge   J. More</li> </ul> <p>Luc Maisonobe did the Java translation.</p>
	 * @param qy  array containing qTy
	 * @param delta  upper bound on the euclidean norm of diagR * lmDir
	 * @param diag  diagonal matrix
	 * @param work1  work array
	 * @param work2  work array
	 * @param work3  work array
	 */
	private void determineLMParameter(double[] qy, double delta, double[] diag,
			double[] work1, double[] work2, double[] work3) {
		Object o_7au3e = null;
		String c_7au3e = "org.apache.commons.math.optimization.general.LevenbergMarquardtOptimizer";
		String msig_7au3e = "determineLMParameter(double[]$double$double[]$double[]$double[]$double[])"
				+ eid_7au3e;
		try {
			determineLMParameter_7au3e(qy, delta, diag, work1, work2, work3);
			addToORefMap(msig_7au3e, null);
			FieldPrinter.print(this, eid_7au3e, c_7au3e, msig_7au3e, 1, 5);
			addToORefMap(msig_7au3e, this);
			FieldPrinter.print(qy, eid_7au3e, c_7au3e, msig_7au3e, 2, 5);
			addToORefMap(msig_7au3e, qy);
			addToORefMap(msig_7au3e, null);
			FieldPrinter.print(diag, eid_7au3e, c_7au3e, msig_7au3e, 4, 5);
			addToORefMap(msig_7au3e, diag);
			FieldPrinter.print(work1, eid_7au3e, c_7au3e, msig_7au3e, 5, 5);
			addToORefMap(msig_7au3e, work1);
			FieldPrinter.print(work2, eid_7au3e, c_7au3e, msig_7au3e, 6, 5);
			addToORefMap(msig_7au3e, work2);
			FieldPrinter.print(work3, eid_7au3e, c_7au3e, msig_7au3e, 7, 5);
			addToORefMap(msig_7au3e, work3);
		} catch (Throwable t7au3e) {
			FieldPrinter.print(t7au3e, eid_7au3e, c_7au3e, msig_7au3e, 0, 5);
			addToORefMap(msig_7au3e, t7au3e);
			throw t7au3e;
		} finally {
			eid_7au3e++;
		}
	}
}
