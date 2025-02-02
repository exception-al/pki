// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.ca;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.dogtagpki.legacy.ca.CAPolicy;
import org.dogtagpki.legacy.policy.IPolicyProcessor;
import org.mozilla.jss.CryptoManager;
import org.mozilla.jss.NicknameConflictException;
import org.mozilla.jss.NotInitializedException;
import org.mozilla.jss.UserCertConflictException;
import org.mozilla.jss.asn1.ASN1Util;
import org.mozilla.jss.asn1.GeneralizedTime;
import org.mozilla.jss.asn1.INTEGER;
import org.mozilla.jss.asn1.InvalidBERException;
import org.mozilla.jss.asn1.OBJECT_IDENTIFIER;
import org.mozilla.jss.asn1.OCTET_STRING;
import org.mozilla.jss.crypto.CryptoStore;
import org.mozilla.jss.crypto.CryptoToken;
import org.mozilla.jss.crypto.KeyPairAlgorithm;
import org.mozilla.jss.crypto.KeyPairGenerator;
import org.mozilla.jss.crypto.NoSuchItemOnTokenException;
import org.mozilla.jss.crypto.PrivateKey;
import org.mozilla.jss.crypto.SignatureAlgorithm;
import org.mozilla.jss.crypto.TokenException;
import org.mozilla.jss.crypto.X509Certificate;
import org.mozilla.jss.netscape.security.pkcs.PKCS10;
import org.mozilla.jss.netscape.security.util.DerOutputStream;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.util.Utils;
import org.mozilla.jss.netscape.security.x509.AlgorithmId;
import org.mozilla.jss.netscape.security.x509.CertificateChain;
import org.mozilla.jss.netscape.security.x509.CertificateIssuerName;
import org.mozilla.jss.netscape.security.x509.CertificateSubjectName;
import org.mozilla.jss.netscape.security.x509.CertificateVersion;
import org.mozilla.jss.netscape.security.x509.RevocationReason;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X500Signer;
import org.mozilla.jss.netscape.security.x509.X509CRLImpl;
import org.mozilla.jss.netscape.security.x509.X509CertImpl;
import org.mozilla.jss.netscape.security.x509.X509CertInfo;
import org.mozilla.jss.netscape.security.x509.X509ExtensionException;
import org.mozilla.jss.netscape.security.x509.X509Key;
import org.mozilla.jss.pkix.cert.Extension;
import org.mozilla.jss.pkix.primitive.Name;

import com.netscape.certsrv.authentication.IAuthToken;
import com.netscape.certsrv.authority.ICertAuthority;
import com.netscape.certsrv.base.BadRequestDataException;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.EPropertyNotFound;
import com.netscape.certsrv.base.IConfigStore;
import com.netscape.certsrv.base.ISubsystem;
import com.netscape.certsrv.base.Nonces;
import com.netscape.certsrv.base.PKIException;
import com.netscape.certsrv.ca.AuthorityID;
import com.netscape.certsrv.ca.CADisabledException;
import com.netscape.certsrv.ca.CAEnabledException;
import com.netscape.certsrv.ca.CAMissingCertException;
import com.netscape.certsrv.ca.CAMissingKeyException;
import com.netscape.certsrv.ca.CANotFoundException;
import com.netscape.certsrv.ca.CANotLeafException;
import com.netscape.certsrv.ca.CATypeException;
import com.netscape.certsrv.ca.ECAException;
import com.netscape.certsrv.ca.ICRLIssuingPoint;
import com.netscape.certsrv.ca.ICertificateAuthority;
import com.netscape.certsrv.ca.IssuerUnavailableException;
import com.netscape.certsrv.cert.CertEnrollmentRequest;
import com.netscape.certsrv.dbs.IDBSubsystem;
import com.netscape.certsrv.dbs.certdb.CertId;
import com.netscape.certsrv.dbs.certdb.ICertRecord;
import com.netscape.certsrv.dbs.certdb.ICertificateRepository;
import com.netscape.certsrv.dbs.crldb.ICRLRepository;
import com.netscape.certsrv.dbs.replicadb.IReplicaIDRepository;
import com.netscape.certsrv.ldap.ELdapException;
import com.netscape.certsrv.logging.ILogger;
import com.netscape.certsrv.logging.event.CRLSigningInfoEvent;
import com.netscape.certsrv.logging.event.CertSigningInfoEvent;
import com.netscape.certsrv.logging.event.OCSPSigningInfoEvent;
import com.netscape.certsrv.ocsp.IOCSPService;
import com.netscape.certsrv.profile.IEnrollProfile;
import com.netscape.certsrv.profile.IProfile;
import com.netscape.certsrv.profile.IProfileSubsystem;
import com.netscape.certsrv.publish.ICRLPublisher;
import com.netscape.certsrv.publish.IPublisherProcessor;
import com.netscape.certsrv.request.ARequestNotifier;
import com.netscape.certsrv.request.IPolicy;
import com.netscape.certsrv.request.IRequest;
import com.netscape.certsrv.request.IRequestListener;
import com.netscape.certsrv.request.IRequestNotifier;
import com.netscape.certsrv.request.IRequestQueue;
import com.netscape.certsrv.request.IRequestScheduler;
import com.netscape.certsrv.request.IService;
import com.netscape.certsrv.request.RequestStatus;
import com.netscape.certsrv.security.ISigningUnit;
import com.netscape.certsrv.util.AsyncLoader;
import com.netscape.certsrv.util.IStatsSubsystem;
import com.netscape.cms.logging.Logger;
import com.netscape.cms.logging.SignedAuditLogger;
import com.netscape.cms.servlet.cert.CertEnrollmentRequestFactory;
import com.netscape.cms.servlet.cert.EnrollmentProcessor;
import com.netscape.cms.servlet.cert.RenewalProcessor;
import com.netscape.cms.servlet.cert.RevocationProcessor;
import com.netscape.cms.servlet.processors.CAProcessor;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;
import com.netscape.cmscore.base.ArgBlock;
import com.netscape.cmscore.dbs.CRLRepository;
import com.netscape.cmscore.dbs.CertRecord;
import com.netscape.cmscore.dbs.CertificateRepository;
import com.netscape.cmscore.dbs.DBSubsystem;
import com.netscape.cmscore.dbs.ReplicaIDRepository;
import com.netscape.cmscore.ldap.PublisherProcessor;
import com.netscape.cmscore.ldapconn.LdapBoundConnFactory;
import com.netscape.cmscore.listeners.ListenerPlugin;
import com.netscape.cmscore.request.RequestSubsystem;
import com.netscape.cmscore.security.KeyCertUtil;
import com.netscape.cmsutil.crypto.CryptoUtil;
import com.netscape.cmsutil.ldap.LDAPPostReadControl;
import com.netscape.cmsutil.ldap.LDAPUtil;
import com.netscape.cmsutil.ocsp.BasicOCSPResponse;
import com.netscape.cmsutil.ocsp.CertID;
import com.netscape.cmsutil.ocsp.CertStatus;
import com.netscape.cmsutil.ocsp.GoodInfo;
import com.netscape.cmsutil.ocsp.KeyHashID;
import com.netscape.cmsutil.ocsp.NameID;
import com.netscape.cmsutil.ocsp.OCSPRequest;
import com.netscape.cmsutil.ocsp.OCSPResponse;
import com.netscape.cmsutil.ocsp.OCSPResponseStatus;
import com.netscape.cmsutil.ocsp.Request;
import com.netscape.cmsutil.ocsp.ResponderID;
import com.netscape.cmsutil.ocsp.ResponseBytes;
import com.netscape.cmsutil.ocsp.ResponseData;
import com.netscape.cmsutil.ocsp.RevokedInfo;
import com.netscape.cmsutil.ocsp.SingleResponse;
import com.netscape.cmsutil.ocsp.TBSRequest;
import com.netscape.cmsutil.ocsp.UnknownInfo;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPAttributeSet;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPConstraints;
import netscape.ldap.LDAPControl;
import netscape.ldap.LDAPEntry;
import netscape.ldap.LDAPException;
import netscape.ldap.LDAPModification;
import netscape.ldap.LDAPModificationSet;
import netscape.ldap.LDAPSearchResults;


/**
 * A class represents a Certificate Authority that is
 * responsible for certificate specific operations.
 * <P>
 *
 * @author lhsiao
 * @version $Revision$, $Date$
 */
public class CertificateAuthority
        implements ICertificateAuthority, ICertAuthority, IOCSPService {

    public final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CertificateAuthority.class);

    private static final Logger signedAuditLogger = SignedAuditLogger.getLogger();

    public static final String OFFICIAL_NAME = "Certificate Manager";

    public final static OBJECT_IDENTIFIER OCSP_NONCE = new OBJECT_IDENTIFIER("1.3.6.1.5.5.7.48.1.2");

    /* The static conn factory is initialised by the host authority's
     * 'init' method, before any lightweight CAs are instantiated
     */
    static LdapBoundConnFactory dbFactory = new LdapBoundConnFactory("CertificateAuthority");

    private static final Map<AuthorityID, ICertificateAuthority> caMap =
        Collections.synchronizedSortedMap(new TreeMap<AuthorityID, ICertificateAuthority>());
    static final Map<AuthorityID, Thread> keyRetrieverThreads =
        Collections.synchronizedSortedMap(new TreeMap<AuthorityID, Thread>());
    protected CertificateAuthority hostCA = null;
    protected AuthorityID authorityID = null;
    protected AuthorityID authorityParentID = null;
    protected BigInteger authoritySerial = null;
    protected String authorityDescription = null;
    protected Collection<String> authorityKeyHosts = null;
    protected boolean authorityEnabled = true;
    private boolean hasKeys = false;
    ECAException signingUnitException = null;

    protected ISubsystem mOwner = null;
    protected IConfigStore mConfig = null;

    protected Logger systemLogger = Logger.getLogger(ILogger.EV_SYSTEM, ILogger.S_CA);

    protected Hashtable<String, ICRLIssuingPoint> mCRLIssuePoints = new Hashtable<String, ICRLIssuingPoint>();
    protected CRLIssuingPoint mMasterCRLIssuePoint = null; // the complete crl.
    protected SigningUnit mSigningUnit;
    protected SigningUnit mOCSPSigningUnit;
    protected SigningUnit mCRLSigningUnit;

    protected CertificateIssuerName mIssuerObj = null;
    protected CertificateSubjectName mSubjectObj = null;
    protected X500Name mName = null;
    protected X500Name mCRLName = null;
    protected X500Name mOCSPName = null;
    protected String mNickname = null; // nickname of CA signing cert.
    protected String mOCSPNickname = null; // nickname of OCSP signing cert.
    protected long mCertSerialNumberCounter = System.currentTimeMillis();
    protected long mRequestID = System.currentTimeMillis();

    protected String[] mAllowedSignAlgors = null;

    protected CertificateRepository mCertRepot = null;
    protected CRLRepository mCRLRepot = null;
    protected ReplicaIDRepository mReplicaRepot = null;

    protected CertificateChain mCACertChain = null;
    protected CertificateChain mOCSPCertChain = null;
    protected X509CertImpl mCRLCert = null;
    protected org.mozilla.jss.crypto.X509Certificate mCRLX509Cert = null;
    protected X509CertImpl mCaCert = null;
    protected org.mozilla.jss.crypto.X509Certificate mCaX509Cert = null;
    protected X509CertImpl mOCSPCert = null;
    protected org.mozilla.jss.crypto.X509Certificate mOCSPX509Cert = null;
    protected String[] mCASigningAlgorithms = null;

    protected PublisherProcessor mPublisherProcessor = null;
    protected IRequestQueue mRequestQueue = null;
    protected CAPolicy mPolicy = null;
    protected CAService mService = null;
    protected IRequestNotifier mNotify = null;
    protected IRequestNotifier mPNotify = null;
    protected long mNumOCSPRequest = 0;
    protected long mTotalTime = 0;
    protected long mTotalData = 0;
    protected long mSignTime = 0;
    protected long mLookupTime = 0;

    protected static final int FASTSIGNING_DISABLED = 0;
    protected static final int FASTSIGNING_ENABLED = 1;

    protected CertificateVersion mDefaultCertVersion;
    protected long mDefaultValidity;
    protected boolean mEnablePastCATime;
    protected boolean mEnableOCSP;
    protected int mFastSigning = FASTSIGNING_DISABLED;

    protected static final long SECOND = 1000; // 1000 milliseconds
    protected static final long MINUTE = 60 * SECOND;
    protected static final long HOUR = 60 * MINUTE;
    protected static final long DAY = 24 * HOUR;
    protected static final long YEAR = DAY * 365;

    protected static final String PROP_CERT_REPOS_DN = "CertificateRepositoryDN";
    protected static final String PROP_REPOS_DN = "RepositoryDN";
    protected static final String PROP_REPLICAID_DN = "dbs.replicadn";

    // for the notification listeners

    /**
     * Package constants
     */

    public IRequestListener mCertIssuedListener = null;
    public IRequestListener mCertRevokedListener = null;
    public IRequestListener mReqInQListener = null;

    /* cache responder ID for performance */
    private ResponderID mResponderIDByName = null;
    private ResponderID mResponderIDByHash = null;

    protected Hashtable<String, ListenerPlugin> mListenerPlugins = null;

    // for CMC shared secret operations
    protected X509Certificate mIssuanceProtCert = null;
    protected PublicKey mIssuanceProtPubKey = null;
    protected PrivateKey mIssuanceProtPrivKey = null;

    /**
     * Internal constants
     */

    protected ICRLPublisher mCRLPublisher = null;
    private String mId = null;

    private boolean mByName = true;

    private boolean mUseNonces = true;
    private int mMaxNonces = 100;

    /* Variables to manage loading and tracking of lightweight CAs */
    static boolean stopped = false;
    private static boolean foundHostAuthority = false;
    AsyncLoader lwcaLoader = new AsyncLoader(10 /*10s timeout*/);

    /* Maps and sets of entryUSNs and nsUniqueIds for avoiding race
     * conditions and unnecessary reloads related to replication */
    private static TreeMap<AuthorityID,BigInteger> entryUSNs = new TreeMap<>();
    private static TreeMap<AuthorityID,String> nsUniqueIds = new TreeMap<>();
    static TreeSet<String> deletedNsUniqueIds = new TreeSet<>();

    /**
     * Constructs a CA subsystem.
     */
    public CertificateAuthority() {
        hostCA = this;
    }

    /**
     * Construct and initialise a lightweight authority
     */
    private CertificateAuthority(
            CertificateAuthority hostCA,
            X500Name dn,
            AuthorityID aid,
            AuthorityID parentAID,
            BigInteger serial,
            String signingKeyNickname,
            Collection<String> authorityKeyHosts,
            String authorityDescription,
            boolean authorityEnabled
            ) throws EBaseException {
        setId(hostCA.getId());
        this.hostCA = hostCA;

        // cert and key may not have been replicated to local nssdb
        // yet, so set DN based on data from LDAP
        this.mName = dn;

        this.authorityID = aid;
        this.authorityParentID = parentAID;
        this.authoritySerial = serial;
        this.authorityDescription = authorityDescription;
        this.authorityEnabled = authorityEnabled;
        mNickname = signingKeyNickname;
        this.authorityKeyHosts = authorityKeyHosts;
        init(hostCA.mOwner, hostCA.mConfig);
    }

    public boolean isHostAuthority() {
        return hostCA == this;
    }

    public void ensureReady()
            throws ECAException {
        if (!authorityEnabled)
            throw new CADisabledException("Authority is disabled");
        if (!isReady()) {
            if (signingUnitException != null)
                throw signingUnitException;
            else
                throw new CAMissingKeyException("Authority does not yet have signing key and cert in local NSSDB");
        }
    }

    public boolean isReady() {
        return hasKeys;
    }

    public boolean getAuthorityEnabled() {
        return authorityEnabled;
    }

    /**
     * Retrieves subsystem identifier.
     */
    public String getId() {
        return mId;
    }

    public CertificateVersion getDefaultCertVersion() {
        return mDefaultCertVersion;
    }

    public boolean isEnablePastCATime() {
        return mEnablePastCATime;
    }

    /**
     * Sets subsystem identifier.
     */
    public void setId(String id) throws EBaseException {
        mId = id;
    }

    /**
     * updates the Master CRL now
     */
    public void updateCRLNow() throws EBaseException {
        if (mMasterCRLIssuePoint != null) {
            mMasterCRLIssuePoint.updateCRLNow();
        }
    }

    public void publishCRLNow() throws EBaseException {
        if (mMasterCRLIssuePoint != null) {
            mMasterCRLIssuePoint.publishCRL();
        }
    }

    public ICRLPublisher getCRLPublisher() {
        return mCRLPublisher;
    }

    public IPolicyProcessor getPolicyProcessor() {
        return mPolicy.getPolicyProcessor();
    }

    public boolean noncesEnabled() {
        return mUseNonces;
    }

    public Map<Object, Long> getNonces(HttpServletRequest request, String name) {

        // Create a new session or use an existing one.
        HttpSession session = request.getSession(true);
        if (session == null) {
            throw new PKIException("Unable to create session.");
        }

        // Lock the session to prevent concurrent access.
        // http://yet-another-dev.blogspot.com/2009/08/synchronizing-httpsession.html

        Object lock = request.getSession().getId().intern();
        synchronized (lock) {

            // Find the existing storage in the session.
            @SuppressWarnings("unchecked")
            Map<Object, Long> nonces = (Map<Object, Long>)session.getAttribute("nonces-"+name);

            if (nonces == null) {
                // If not present, create a new storage.
                nonces = Collections.synchronizedMap(new Nonces(mMaxNonces));

                // Put the storage in the session.
                session.setAttribute("nonces-"+name, nonces);
            }

            return nonces;
        }
    }

    /**
     * Initializes this CA subsystem.
     * <P>
     *
     * @param owner owner of this subsystem
     * @param config configuration of this subsystem
     * @exception EBaseException failed to initialize this CA
     */
    public void init(ISubsystem owner, IConfigStore config) throws
            EBaseException {

        logger.info("CertificateAuthority: initialization");

        CMSEngine engine = CMS.getCMSEngine();
        IConfigStore cs = engine.getConfigStore();
        IConfigStore dbCfg = cs.getSubStore("internaldb");

        try {
            mOwner = owner;
            mConfig = config;

            if (isHostAuthority()) {
                dbFactory.init(cs, dbCfg, engine.getPasswordStore());
            }

            // init cert & crl database
            initCertDatabase();
            initCrlDatabase();

            // init replica id repository
            if (isHostAuthority()) {
                String replicaReposDN = mConfig.getString(PROP_REPLICAID_DN, null);
                if (replicaReposDN == null) {
                    replicaReposDN = "ou=Replica," + getDBSubsystem().getBaseDN();
                }
                mReplicaRepot = new ReplicaIDRepository(
                        DBSubsystem.getInstance(), 1, replicaReposDN);
                logger.debug("Replica Repot inited");
            } else {
                mReplicaRepot = hostCA.mReplicaRepot;
            }

            // init signing unit & CA cert.
            boolean initSigUnitSucceeded = false;
            try {
                try {
                    logger.info("CertificateAuthority: initializing signing unit for CA");
                    initSigUnit();
                    initSigUnitSucceeded = true;

                } catch (CAMissingCertException | CAMissingKeyException e) {
                    logger.warn("CertificateAuthority: CA signing key and cert not (yet) present in NSS database");
                    signingUnitException = e;

                    if (authorityID == null) {
                        // Only the host authority should ever see a
                        // null authorityID, e.g. during two-step
                        // installation of externally-signed CA.
                        logger.debug("null authorityID -> host authority; not starting KeyRetriever");

                    } else if (!keyRetrieverThreads.containsKey(authorityID)) {
                        logger.info("CertificateAuthority: starting KeyRetrieverRunner thread");
                        Thread t = new Thread(
                            new KeyRetrieverRunner(this, authorityID, mNickname, authorityKeyHosts),
                            "KeyRetrieverRunner-" + authorityID);
                        t.start();
                        keyRetrieverThreads.put(authorityID, t);

                    } else {
                        logger.debug("KeyRetriever thread already running for authority " + authorityID);
                    }
                }

                // init default CA attributes like cert version, validity.
                initDefCaAttrs();

            } catch (EBaseException e) {
                if (engine.isPreOpMode()) {
                    logger.warn("CertificateAuthority: " + e.getMessage(), e);
                    logger.warn("CertificateAuthority: swallow exception in pre-op mode");
                } else {
                    logger.error("CertificateAuthority: " + e.getMessage(), e);
                    throw e;
                }
            }

            /* Don't try to update the cert unless we already have
             * the cert and key. */
            if (initSigUnitSucceeded)
                checkForNewerCert();

            mUseNonces = mConfig.getBoolean("enableNonces", true);
            mMaxNonces = mConfig.getInteger("maxNumberOfNonces", 100);

            // init request queue and related modules.
            logger.debug("CertificateAuthority init: initRequestQueue");
            initRequestQueue();
            if (engine.isPreOpMode()) {
                logger.debug("CertificateAuthority.init(): Abort in pre-op mode");
                return;
            }

            /* The host CA owns these resources so skip these
             * steps for lightweight CAs.
             */
            if (isHostAuthority()) {
                /* These methods configure and start threads related to
                 * CertificateRepository.  Ideally all of the config would
                 * be pushed into CertificateRepository constructor and a
                 * single 'start' method would start the threads.
                 */
                // set certificate status to 10 minutes
                mCertRepot.setCertStatusUpdateInterval(
                    mRequestQueue.getRequestRepository(),
                    mConfig.getInteger("certStatusUpdateInterval", 10 * 60),
                    mConfig.getBoolean("listenToCloneModifications", false));
                mCertRepot.setConsistencyCheck(
                    mConfig.getBoolean("ConsistencyCheck", false));
                mCertRepot.setSkipIfInConsistent(
                    mConfig.getBoolean("SkipIfInConsistent", false));

                // set serial number update task to run every 10 minutes
                mCertRepot.setSerialNumberUpdateInterval(
                    mRequestQueue.getRequestRepository(),
                    mConfig.getInteger("serialNumberUpdateInterval", 10 * 60));

                mService.init(config.getSubStore("connector"));

                initMiscellaneousListeners();
            }

            initCRLPublisher();

            // initialize publisher processor (publish remote admin
            // rely on this subsystem, so it has to be initialized)
            initPublish();

            // Initialize CRL issuing points.
            // note CRL framework depends on DBS, CRYPTO and PUBLISHING
            // being functional.
            initCRL();

            if (isHostAuthority() && haveLightweightCAsContainer()) {
                new Thread(new AuthorityMonitor(this), "AuthorityMonitor").start();
                try {
                    // block until the expected number of authorities
                    // have been loaded (based on numSubordinates of
                    // container entry), or watchdog times it out (in case
                    // numSubordinates is larger than the number of entries
                    // we can see, e.g. replication conflict entries).
                    lwcaLoader.awaitLoadDone();
                } catch (InterruptedException e) {
                    logger.warn("CertificateAuthority: caught InterruptedException "
                            + "while waiting for initial load of authorities.");
                    logger.warn("You may have replication conflict entries or "
                            + "extraneous data under " + authorityBaseDN());
                }

                if (!foundHostAuthority) {
                    logger.debug("loadLightweightCAs: no entry for host authority");
                    logger.debug("loadLightweightCAs: adding entry for host authority");
                    caMap.put(addHostAuthorityEntry(), this);
                }

                logger.debug("CertificateAuthority: finished init of host authority");
            }

            // set up CA Issuance Protection Cert
            if (initSigUnitSucceeded)
                initIssuanceProtectionCert();
        } catch (EBaseException e) {
            if (engine.isPreOpMode()) {
                logger.warn("CertificateAuthority: " + e.getMessage(), e);
                logger.warn("CertificateAuthority: swallow exception in pre-op mode");
            } else {
                logger.error("CertificateAuthority: " + e.getMessage(), e);
                throw e;
            }
        }
    }

    private void generateSigningInfoAuditEvents()
            throws EBaseException {
        try {

            if (isHostAuthority()) {

                // For host CA, generate cert, OCSP, and CRL signing info without authority ID.

                String certSigningSKI = CryptoUtil.getSKIString(mSigningUnit.getCertImpl());
                signedAuditLogger.log(CertSigningInfoEvent.createSuccessEvent(ILogger.SYSTEM_UID, certSigningSKI));

                String ocspSigningSKI = CryptoUtil.getSKIString(mOCSPSigningUnit.getCertImpl());
                signedAuditLogger.log(OCSPSigningInfoEvent.createSuccessEvent(ILogger.SYSTEM_UID, ocspSigningSKI));

                String crlSigningSKI = CryptoUtil.getSKIString(mCRLSigningUnit.getCertImpl());
                signedAuditLogger.log(CRLSigningInfoEvent.createSuccessEvent(ILogger.SYSTEM_UID, crlSigningSKI));

            } else {

                // For lightweight sub CA, generate cert and OCSP signing info with authority ID.
                // Don't generate CRL signing info since it doesn't support CRL.

                String certSigningSKI = CryptoUtil.getSKIString(mSigningUnit.getCertImpl());
                signedAuditLogger.log(CertSigningInfoEvent.createSuccessEvent(ILogger.SYSTEM_UID, certSigningSKI, authorityID));

                String ocspSigningSKI = CryptoUtil.getSKIString(mOCSPSigningUnit.getCertImpl());
                signedAuditLogger.log(OCSPSigningInfoEvent.createSuccessEvent(ILogger.SYSTEM_UID, ocspSigningSKI, authorityID));
            }

        } catch (IOException e) {
            throw new EBaseException(e);
        }
    }

    /**
     * initIssuanceProtectionCert sets the CA Issuance Protection cert
     */
    private void initIssuanceProtectionCert()
           throws EBaseException {
        String method = "CertificateAuthority: initIssuanceProtectionCert: ";
        CryptoManager cManager = null;

        String name = null;
        String defaultName = "cert.subsystem.nickname";
        String certNickName = null;
        try {
            cManager = CryptoManager.getInstance();
            name = "cert.issuance_protection.nickname";
            logger.debug(method + " about to look for CA Issuance Protection cert: "+
                name);
            certNickName = mConfig.getString(name);
        } catch (EBaseException e) {
            logger.debug(method + name + " not found; use defaultName : " + defaultName );
            name = defaultName ;
            certNickName = mConfig.getString(name);
        } catch (Exception e) {
            throw new EBaseException(method + e);
        }
        logger.debug(method + "found nickname: "+ certNickName);

        try {
                mIssuanceProtCert = cManager.findCertByNickname(certNickName);
            if (mIssuanceProtCert != null) {
                logger.debug(method + " found CA Issuance Protection cert:" + certNickName);
                mIssuanceProtPubKey = mIssuanceProtCert.getPublicKey();
                mIssuanceProtPrivKey = cManager.findPrivKeyByCert(mIssuanceProtCert);
            }
        } catch (Exception e) {
            throw new EBaseException(method + e);
        }
    }

    public PublicKey getIssuanceProtPubKey() {
        return mIssuanceProtPubKey;
    }

    public PrivateKey getIssuanceProtPrivKey() {
        return mIssuanceProtPrivKey;
    }

    public X509Certificate getIssuanceProtCert() {
        return mIssuanceProtCert;
    }

    private void checkForNewerCert() throws EBaseException {
        if (authoritySerial == null)
            return;
        if (authoritySerial.equals(mCaCert.getSerialNumber()))
            return;

        // The authoritySerial recorded in LDAP differs from the
        // certificate in NSSDB.  Import the newer cert.
        //
        // Note that the new serial number need not be greater,
        // e.g. if random serial numbers are enabled.
        //
        logger.debug(
            "CertificateAuthority: Updating certificate in NSSDB; new serial number: "
            + authoritySerial);

        try {
            X509Certificate oldCert = mCaX509Cert;
            CryptoManager manager = CryptoManager.getInstance();

            // add new cert
            X509CertImpl newCert = mCertRepot.getX509Certificate(authoritySerial);
            manager.importUserCACertPackage(newCert.getEncoded(), mNickname);

            // delete old cert
            manager.getInternalKeyStorageToken().getCryptoStore()
                .deleteCert(oldCert);

            logger.info("CertificateAuthority: reinitializing signing unit after new certificate");
            initSigUnit();

        } catch (CAMissingCertException e) {
            logger.warn("CertificateAuthority: CA signing cert not (yet) present in NSS database");
            signingUnitException = e;

        } catch (CAMissingKeyException e) {
            logger.warn("CertificateAuthority: CA signing key not (yet) present in NSS database");
            signingUnitException = e;

        } catch (CertificateException e) {
            throw new ECAException("Failed to update certificate", e);
        } catch (NotInitializedException e) {
            throw new ECAException("CryptoManager not initialized", e);
        } catch (NicknameConflictException e) {
            throw new ECAException("Failed to update certificate; nickname conflict", e);
        } catch (UserCertConflictException e) {
            throw new ECAException("Failed to update certificate; user cert conflict", e);
        } catch (TokenException | NoSuchItemOnTokenException e) {
            // really shouldn't happen
            throw new ECAException("Failed to update certificate", e);
        }
    }

    String authorityBaseDN() {
        return "ou=authorities,ou=" + getId()
            + "," + getDBSubsystem().getBaseDN();
    }

    private boolean haveLightweightCAsContainer() throws ELdapException {
        LDAPConnection conn = dbFactory.getConn();
        try {
            LDAPSearchResults results = conn.search(
                authorityBaseDN(), LDAPConnection.SCOPE_BASE, null, null, false);
            return results != null;
        } catch (LDAPException e) {
            return false;
        } finally {
            dbFactory.returnConn(conn);
        }
    }

    private boolean entryUSNPluginEnabled() {
        try {
            LDAPConnection conn = dbFactory.getConn();
            try {
                LDAPSearchResults results = conn.search(
                    "cn=usn,cn=plugins,cn=config", LDAPConnection.SCOPE_BASE,
                    "(nsslapd-pluginEnabled=on)", null, false);
                return results != null && results.hasMoreElements();
            } catch (LDAPException e) {
                return false;
            } finally {
                dbFactory.returnConn(conn);
            }
        } catch (ELdapException e) {
            return false;  // oh well
        }
    }

    private void initCRLPublisher() throws EBaseException {
        // instantiate CRL publisher
        if (!isHostAuthority()) {
            mByName = hostCA.mByName;
            mCRLPublisher = hostCA.mCRLPublisher;
            return;
        }

        mByName = mConfig.getBoolean("byName", true);
        IConfigStore cpStore = mConfig.getSubStore("crlPublisher");
        if (cpStore != null && cpStore.size() > 0) {
            String publisherClass = cpStore.getString("class");

            if (publisherClass != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<ICRLPublisher> pc = (Class<ICRLPublisher>) Class.forName(publisherClass);

                    mCRLPublisher = pc.newInstance();
                    mCRLPublisher.init(this, cpStore);
                } catch (ClassNotFoundException ee) {
                    log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_PUBLISHER", ee.toString()));
                } catch (IllegalAccessException ee) {
                    log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_PUBLISHER", ee.toString()));
                } catch (InstantiationException ee) {
                    log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_PUBLISHER", ee.toString()));
                }
            }
        }
    }

    /**
     * return CA's request queue processor
     */
    public IRequestQueue getRequestQueue() {
        return mRequestQueue;
    }

    /**
     * registers listener
     */
    public void registerRequestListener(IRequestListener listener) {
        mNotify.registerListener(listener);
    }

    /**
     * registers listener with a name.
     */
    public void registerRequestListener(String name, IRequestListener listener) {
        mNotify.registerListener(name, listener);
    }

    /**
     * removes listener
     */
    public void removeRequestListener(IRequestListener listener) {
        mNotify.removeListener(listener);
    }

    /**
     * removes listener with a name.
     */
    public void removeRequestListener(String name) {
        mNotify.removeListener(name);
    }

    /**
     * register listener for pending requests
     */
    public void registerPendingListener(IRequestListener listener) {
        mPNotify.registerListener(listener);
    }

    /**
     * register listener for pending requests with a name.
     */
    public void registerPendingListener(String name, IRequestListener listener) {
        mPNotify.registerListener(name, listener);
    }

    /**
     * get listener from listener list
     */
    public IRequestListener getRequestListener(String name) {
        return mNotify.getListener(name);
    }

    /**
     * get notifiers registered by CA
     */
    public IRequestNotifier getRequestNotifier() {
        return mNotify;
    }

    /**
     * get listener from listener list
     */
    public IRequestListener getPendingListener(String name) {
        return mPNotify.getListener(name);
    }

    public Enumeration<String> getRequestListenerNames() {
        return mNotify.getListenerNames();
    }

    public IRequestListener getRequestInQListener() {
        return mReqInQListener;
    }

    public IRequestListener getCertIssuedListener() {
        return mCertIssuedListener;
    }

    public IRequestListener getCertRevokedListener() {
        return mCertRevokedListener;
    }

    /**
     * return CA's policy processor.
     */
    public IPolicy getCAPolicy() {
        return mPolicy;
    }

    /**
     * return CA's request queue service object.
     */
    public IService getCAService() {
        return mService;
    }

    /**
     * check if the ca is a clone.
     */
    public boolean isClone() {
        if (CAService.mCLAConnector != null)
            return true;
        else
            return false;
    }

    /**
     * Starts up this subsystem.
     */
    public void startup() throws EBaseException {
        CMSEngine engine = CMS.getCMSEngine();
        if (engine.isPreOpMode()) {
            logger.debug("CertificateAuthority.startup(): Do not start CA in pre-op mode");
            return;
        }
        mService.startup();
        mRequestQueue.recover();

        if (isHostAuthority()) {
            // setup Admin operations
            initNotificationListeners();
            startPublish();
        }
    }

    /**
     * Shutdowns this subsystem.
     * <P>
     */
    public void shutdown() {
        // lightweight authorities don't own these resources
        if (!isHostAuthority())
            return;

        Enumeration<ICRLIssuingPoint> enums = mCRLIssuePoints.elements();
        while (enums.hasMoreElements()) {
            CRLIssuingPoint point = (CRLIssuingPoint) enums.nextElement();
            point.shutdown();
        }
        mCRLIssuePoints.clear();

        if (mMasterCRLIssuePoint != null) {
            mMasterCRLIssuePoint.shutdown();
        }

        if (mCertRepot != null) {
            mCertRepot.shutdown();
        }

        if (mPublisherProcessor != null) {
            mPublisherProcessor.shutdown();
        }

        /* Stop the activityMonitor thread
         *
         * dbFactory.reset() will disconnect all connections,
         * causing the current conn.search() to throw.
         * The search will not be restarted because 'stopped' has
         * set, and the monitor thread will exit.
         */
        stopped = true;
        try {
            dbFactory.reset();
        } catch (ELdapException e) {
            logger.warn("CertificateAuthority.shutdown: failed to reset "
                    + "dbFactory: " + e);
            // not much else we can do here.
        }
    }

    /**
     * Retrieves the configuration store of this subsystem.
     * <P>
     */
    public IConfigStore getConfigStore() {
        return mConfig;
    }

    /**
     * Retrieves database services.
     */
    public IDBSubsystem getDBSubsystem() {
        return DBSubsystem.getInstance();
    }

    public void setValidity(String enableCAPast) throws EBaseException {
        if (enableCAPast.equals("true"))
            mEnablePastCATime = true;
        else
            mEnablePastCATime = false;
        mConfig.putString(PROP_ENABLE_PAST_CATIME, enableCAPast);
    }

    public long getDefaultValidity() {
        return mDefaultValidity;
    }

    public SignatureAlgorithm getDefaultSignatureAlgorithm() {
        return mSigningUnit.getDefaultSignatureAlgorithm();
    }

    public String getDefaultAlgorithm() {
        return mSigningUnit.getDefaultAlgorithm();
    }

    public void setDefaultAlgorithm(String algorithm) throws EBaseException {
        mSigningUnit.setDefaultAlgorithm(algorithm);
    }

    public String getStartSerial() {
        try {
            BigInteger serial =
                    mCertRepot.peekNextSerialNumber();

            if (serial == null)
                return "";
            else
                return serial.toString(16);
        } catch (EBaseException e) {
            // shouldn't get here.
            return "";
        }
    }

    public void setStartSerial(String serial) throws EBaseException {
        mCertRepot.setTheSerialNumber(new BigInteger(serial));
    }

    public String getMaxSerial() {
        String serial = mCertRepot.getMaxSerial();

        if (serial != null)
            return serial;
        else
            return "";
    }

    public void setMaxSerial(String serial) throws EBaseException {
        mCertRepot.setMaxSerial(serial);
    }

    /**
     * Retrieves certificate repository.
     * <P>
     *
     * @return certificate repository
     */
    public ICertificateRepository getCertificateRepository() {
        return mCertRepot;
    }

    /**
     * Retrieves replica repository.
     * <P>
     *
     * @return replica repository
     */
    public IReplicaIDRepository getReplicaRepository() {
        return mReplicaRepot;
    }

    /**
     * Retrieves CRL repository.
     */
    public ICRLRepository getCRLRepository() {
        return mCRLRepot;
    }

    public IPublisherProcessor getPublisherProcessor() {
        return mPublisherProcessor;
    }

    /**
     * Retrieves the CRL issuing point by id.
     * <P>
     *
     * @param id string id of the CRL issuing point
     * @return CRL issuing point
     */
    public ICRLIssuingPoint getCRLIssuingPoint(String id) {
        return mCRLIssuePoints.get(id);
    }

    /**
     * Enumerates CRL issuing points
     * <P>
     *
     * @return security service
     */
    public Enumeration<ICRLIssuingPoint> getCRLIssuingPoints() {
        return mCRLIssuePoints.elements();
    }

    public int getCRLIssuingPointsSize() {
        return mCRLIssuePoints.size();
    }

    /**
     * Adds CRL issuing point with the given identifier and description.
     */
    @SuppressWarnings("unchecked")
    public boolean addCRLIssuingPoint(IConfigStore crlSubStore, String id,
                                      boolean enable, String description) {
        crlSubStore.makeSubStore(id);
        IConfigStore c = crlSubStore.getSubStore(id);

        if (c != null) {
            c.putString("allowExtensions", "true");
            c.putString("alwaysUpdate", "false");
            c.putString("autoUpdateInterval", "240");
            c.putString("caCertsOnly", "false");
            c.putString("cacheUpdateInterval", "15");
            c.putString("class", "com.netscape.ca.CRLIssuingPoint");
            c.putString("dailyUpdates", "3:45");
            c.putString("description", description);
            c.putBoolean("enable", enable);
            c.putString("enableCRLCache", "true");
            c.putString("enableCRLUpdates", "true");
            c.putString("enableCacheTesting", "false");
            c.putString("enableCacheRecovery", "true");
            c.putString("enableDailyUpdates", "false");
            c.putString("enableUpdateInterval", "true");
            c.putString("extendedNextUpdate", "true");
            c.putString("includeExpiredCerts", "false");
            c.putString("minUpdateInterval", "0");
            c.putString("nextUpdateGracePeriod", "0");
            c.putString("publishOnStart", "false");
            c.putString("saveMemory", "false");
            c.putString("signingAlgorithm", "SHA256withRSA");
            c.putString("updateSchema", "1");

            // crl extensions
            // AuthorityInformationAccess
            c.putString("extension.AuthorityInformationAccess.enable", "false");
            c.putString("extension.AuthorityInformationAccess.critical", "false");
            c.putString("extension.AuthorityInformationAccess.type", "CRLExtension");
            c.putString("extension.AuthorityInformationAccess.class",
                    "com.netscape.cms.crl.CMSAuthInfoAccessExtension");
            c.putString("extension.AuthorityInformationAccess.numberOfAccessDescriptions", "1");
            c.putString("extension.AuthorityInformationAccess.accessMethod0", "caIssuers");
            c.putString("extension.AuthorityInformationAccess.accessLocationType0", "URI");
            c.putString("extension.AuthorityInformationAccess.accessLocation0", "");
            // AuthorityKeyIdentifier
            c.putString("extension.AuthorityKeyIdentifier.enable", "false");
            c.putString("extension.AuthorityKeyIdentifier.critical", "false");
            c.putString("extension.AuthorityKeyIdentifier.type", "CRLExtension");
            c.putString("extension.AuthorityKeyIdentifier.class",
                    "com.netscape.cms.crl.CMSAuthorityKeyIdentifierExtension");
            // IssuerAlternativeName
            c.putString("extension.IssuerAlternativeName.enable", "false");
            c.putString("extension.IssuerAlternativeName.critical", "false");
            c.putString("extension.IssuerAlternativeName.type", "CRLExtension");
            c.putString("extension.IssuerAlternativeName.class",
                    "com.netscape.cms.crl.CMSIssuerAlternativeNameExtension");
            c.putString("extension.IssuerAlternativeName.numNames", "0");
            c.putString("extension.IssuerAlternativeName.nameType0", "");
            c.putString("extension.IssuerAlternativeName.name0", "");
            // CRLNumber
            c.putString("extension.CRLNumber.enable", "true");
            c.putString("extension.CRLNumber.critical", "false");
            c.putString("extension.CRLNumber.type", "CRLExtension");
            c.putString("extension.CRLNumber.class",
                    "com.netscape.cms.crl.CMSCRLNumberExtension");
            // DeltaCRLIndicator
            c.putString("extension.DeltaCRLIndicator.enable", "false");
            c.putString("extension.DeltaCRLIndicator.critical", "true");
            c.putString("extension.DeltaCRLIndicator.type", "CRLExtension");
            c.putString("extension.DeltaCRLIndicator.class",
                    "com.netscape.cms.crl.CMSDeltaCRLIndicatorExtension");
            // IssuingDistributionPoint
            c.putString("extension.IssuingDistributionPoint.enable", "false");
            c.putString("extension.IssuingDistributionPoint.critical", "true");
            c.putString("extension.IssuingDistributionPoint.type", "CRLExtension");
            c.putString("extension.IssuingDistributionPoint.class",
                    "com.netscape.cms.crl.CMSIssuingDistributionPointExtension");
            c.putString("extension.IssuingDistributionPoint.pointType", "");
            c.putString("extension.IssuingDistributionPoint.pointName", "");
            c.putString("extension.IssuingDistributionPoint.onlyContainsUserCerts", "false");
            c.putString("extension.IssuingDistributionPoint.onlyContainsCACerts", "false");
            c.putString("extension.IssuingDistributionPoint.onlySomeReasons", "");
            //"keyCompromise,cACompromise,affiliationChanged,superseded,cessationOfOperation,certificateHold");
            c.putString("extension.IssuingDistributionPoint.indirectCRL", "false");
            // CRLReason
            c.putString("extension.CRLReason.enable", "true");
            c.putString("extension.CRLReason.critical", "false");
            c.putString("extension.CRLReason.type", "CRLEntryExtension");
            c.putString("extension.CRLReason.class",
                    "com.netscape.cms.crl.CMSCRLReasonExtension");
            // HoldInstruction - removed by RFC 5280
            // c.putString("extension.HoldInstruction.enable", "false");
            // c.putString("extension.HoldInstruction.critical", "false");
            // c.putString("extension.HoldInstruction.type", "CRLEntryExtension");
            // c.putString("extension.HoldInstruction.class",
            //     "com.netscape.cms.crl.CMSHoldInstructionExtension");
            // c.putString("extension.HoldInstruction.instruction", "none");
            // InvalidityDate
            c.putString("extension.InvalidityDate.enable", "true");
            c.putString("extension.InvalidityDate.critical", "false");
            c.putString("extension.InvalidityDate.type", "CRLEntryExtension");
            c.putString("extension.InvalidityDate.class",
                    "com.netscape.cms.crl.CMSInvalidityDateExtension");
            // CertificateIssuer
            /*
             c.putString("extension.CertificateIssuer.enable", "false");
             c.putString("extension.CertificateIssuer.critical", "true");
             c.putString("extension.CertificateIssuer.type", "CRLEntryExtension");
             c.putString("extension.CertificateIssuer.class",
             "com.netscape.cms.crl.CMSCertificateIssuerExtension");
             c.putString("extension.CertificateIssuer.numNames", "0");
             c.putString("extension.CertificateIssuer.nameType0", "");
             c.putString("extension.CertificateIssuer.name0", "");
             */
            // FreshestCRL
            c.putString("extension.FreshestCRL.enable", "false");
            c.putString("extension.FreshestCRL.critical", "false");
            c.putString("extension.FreshestCRL.type", "CRLExtension");
            c.putString("extension.FreshestCRL.class",
                    "com.netscape.cms.crl.CMSFreshestCRLExtension");
            c.putString("extension.FreshestCRL.numPoints", "0");
            c.putString("extension.FreshestCRL.pointType0", "");
            c.putString("extension.FreshestCRL.pointName0", "");

            String issuingPointClassName = null;
            Class<CRLIssuingPoint> issuingPointClass = null;
            CRLIssuingPoint issuingPoint = null;

            try {
                issuingPointClassName = c.getString(PROP_CLASS);
                issuingPointClass = (Class<CRLIssuingPoint>) Class.forName(issuingPointClassName);
                issuingPoint = issuingPointClass.newInstance();
                issuingPoint.init(this, id, c);
                mCRLIssuePoints.put(id, issuingPoint);
            } catch (EPropertyNotFound e) {
                crlSubStore.removeSubStore(id);
                return false;
            } catch (EBaseException e) {
                crlSubStore.removeSubStore(id);
                return false;
            } catch (ClassNotFoundException e) {
                crlSubStore.removeSubStore(id);
                return false;
            } catch (InstantiationException e) {
                crlSubStore.removeSubStore(id);
                return false;
            } catch (IllegalAccessException e) {
                crlSubStore.removeSubStore(id);
                return false;
            }
        }
        return true;
    }

    /**
     * Deletes CRL issuing point with the given identifier.
     */
    public void deleteCRLIssuingPoint(IConfigStore crlSubStore, String id) {
        CRLIssuingPoint ip = (CRLIssuingPoint) mCRLIssuePoints.get(id);

        if (ip != null) {
            ip.shutdown();
            mCRLIssuePoints.remove(id);
            ip = null;
            crlSubStore.removeSubStore(id);
            try {
                mCRLRepot.deleteCRLIssuingPointRecord(id);
            } catch (EBaseException e) {
                log(ILogger.LL_FAILURE,
                        CMS.getLogMessage("FAILED_REMOVING_CRL_IP_2", id, e.toString()));
            }
        }
    }

    /**
     * Returns X500 name of the Certificate Authority
     * <P>
     *
     * @return CA name
     */
    public X500Name getX500Name() {
        return mName;
    }

    public CertificateIssuerName getIssuerObj() {
       return mIssuerObj;
    }

    public CertificateSubjectName getSubjectObj() {
       return mSubjectObj;
    }

    public X500Name getCRLX500Name() {
        return mCRLName;
    }

    public X500Name getOCSPX500Name() {
        return mOCSPName;
    }

    /**
     * Returns nickname of CA's signing cert.
     * <p>
     *
     * @return CA signing cert nickname.
     */
    public String getNickname() {
        return mNickname;
    }

    /**
     * Returns nickname of OCSP's signing cert.
     * <p>
     *
     * @return OCSP signing cert nickname.
     */
    public String getOCSPNickname() {
        return mOCSPNickname;
    }

    /**
     * Returns default signing unit used by this CA
     * <P>
     *
     * @return request identifier
     */
    public ISigningUnit getSigningUnit() {
        return mSigningUnit;
    }

    public ISigningUnit getCRLSigningUnit() {
        return mCRLSigningUnit;
    }

    public ISigningUnit getOCSPSigningUnit() {
        return mOCSPSigningUnit;
    }

    public void setBasicConstraintMaxLen(int num) {
        mConfig.putString("Policy.rule.BasicConstraintsExt.maxPathLen", "" + num);
    }

    /**
     * Signs CRL using the specified signature algorithm.
     * If no algorithm is specified the CA's default signing algorithm
     * is used.
     * <P>
     *
     * @param crl the CRL to be signed.
     * @param algname the algorithm name to use. This is a JCA name such
     *            as MD5withRSA, etc. If set to null the default signing algorithm
     *            is used.
     *
     * @return the signed CRL
     */
    public X509CRLImpl sign(X509CRLImpl crl, String algname)
            throws EBaseException {

        CMSEngine engine = CMS.getCMSEngine();
        ensureReady();
        X509CRLImpl signedcrl = null;

        IStatsSubsystem statsSub = (IStatsSubsystem) engine.getSubsystem(IStatsSubsystem.ID);
        if (statsSub != null) {
            statsSub.startTiming("signing");
        }

        try (DerOutputStream out = new DerOutputStream()) {
            DerOutputStream tmp = new DerOutputStream();

            if (algname == null) {
                algname = mSigningUnit.getDefaultAlgorithm();
            }

            crl.encodeInfo(tmp);
            AlgorithmId.get(algname).encode(tmp);

            byte[] tbsCertList = crl.getTBSCertList();

            byte[] signature = mCRLSigningUnit.sign(tbsCertList, algname);

            if (crl.setSignature(signature)) {
                tmp.putBitString(signature);
                out.write(DerValue.tag_Sequence, tmp);

                if (crl.setSignedCRL(out.toByteArray())) {
                    signedcrl = crl;
                    // signedcrl = new X509CRLImpl(out.toByteArray());
                } else {
                    logger.warn("Failed to add signed-CRL to CRL object.");
                }
            } else {
                logger.warn("Failed to add signature to CRL object.");
            }
        } catch (CRLException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_SIGN_CRL", e.toString(), e.getMessage()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_SIGNING_CRL_FAILED", e.getMessage()));
        } catch (X509ExtensionException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_SIGN_CRL", e.toString(), e.getMessage()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_SIGNING_CRL_FAILED", e.getMessage()));
        } catch (NoSuchAlgorithmException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_SIGN_CRL", e.toString(), e.getMessage()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_SIGNING_CRL_FAILED", e.getMessage()));
        } catch (IOException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_SIGN_CRL", e.toString(), e.getMessage()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_SIGNING_CRL_FAILED", e.getMessage()));
        } finally {
            if (statsSub != null) {
                statsSub.endTiming("signing");
            }
        }

        return signedcrl;
    }

    /**
     * Signs the given certificate info using specified signing algorithm
     * If no algorithm is specified the CA's default algorithm is used.
     * <P>
     *
     * @param certInfo the certificate info to be signed.
     * @param algname the signing algorithm to use. These are names defined
     *            in JCA, such as MD5withRSA, etc. If null the CA's default
     *            signing algorithm will be used.
     * @return signed certificate
     */
    public X509CertImpl sign(X509CertInfo certInfo, String algname)
            throws EBaseException {

        CMSEngine engine = CMS.getCMSEngine();
        ensureReady();

        X509CertImpl signedcert = null;

        IStatsSubsystem statsSub = (IStatsSubsystem) engine.getSubsystem(IStatsSubsystem.ID);
        if (statsSub != null) {
            statsSub.startTiming("signing");
        }

        try (DerOutputStream out = new DerOutputStream();
                DerOutputStream tmp = new DerOutputStream()) {

            if (certInfo == null) {
                log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_CERTINFO"));
                return null;
            }

            if (algname == null) {
                algname = mSigningUnit.getDefaultAlgorithm();
            }

            logger.debug("sign cert get algorithm");
            AlgorithmId alg = AlgorithmId.get(algname);

            // encode certificate info
            logger.debug("sign cert encoding cert");
            certInfo.encode(tmp);
            byte[] rawCert = tmp.toByteArray();

            // encode algorithm identifier
            logger.debug("sign cert encoding algorithm");
            alg.encode(tmp);

            logger.debug("CA cert signing: signing cert");
            byte[] signature = mSigningUnit.sign(rawCert, algname);

            tmp.putBitString(signature);

            // Wrap the signed data in a SEQUENCE { data, algorithm, sig }
            out.write(DerValue.tag_Sequence, tmp);
            //log(ILogger.LL_INFO, "CertificateAuthority: done signing");

            switch (mFastSigning) {
            case FASTSIGNING_DISABLED:
                signedcert = new X509CertImpl(out.toByteArray());
                break;

            case FASTSIGNING_ENABLED:
                signedcert = new X509CertImpl(out.toByteArray(), certInfo);
                break;

            default:
                break;
            }
        } catch (NoSuchAlgorithmException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_SIGN_CERT", e.toString(), e.getMessage()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_SIGNING_CERT_FAILED", e.getMessage()));
        } catch (IOException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_SIGN_CERT", e.toString(), e.getMessage()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_SIGNING_CERT_FAILED", e.getMessage()));
        } catch (CertificateException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_SIGN_CERT", e.toString(), e.getMessage()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_SIGNING_CERT_FAILED", e.getMessage()));
        } finally {
            if (statsSub != null) {
                statsSub.endTiming("signing");
            }
        }
        return signedcert;
    }

    /**
     * Sign a byte array using the specified algorithm.
     * If algorithm is null the CA's default algorithm is used.
     * <p>
     *
     * @param data the data to be signed in a byte array.
     * @param algname the algorithm to use.
     * @return the signature in a byte array.
     */
    public byte[] sign(byte[] data, String algname)
            throws EBaseException {
        ensureReady();
        return mSigningUnit.sign(data, algname);
    }

    /**
     * logs a message in the CA area.
     *
     * @param level the debug level.
     * @param msg the message to debug.
     */
    public void log(int level, String msg) {
        systemLogger.log(level, msg);
    }

    /**
     * Retrieves certificate chains of this CA.
     *
     * @return this CA's cert chain.
     */
    public CertificateChain getCACertChain() {
        return mCACertChain;
    }

    public X509CertImpl getCACert() throws EBaseException {

        if (mCaCert != null) {
            return mCaCert;
        }

        String cert = mConfig.getString("signing.cert");
        logger.debug("CertificateAuthority: CA signing cert: " + cert);

        if (StringUtils.isEmpty(cert)) {
            logger.error("CertificateAuthority: Missing CA signing certificate");
            throw new EBaseException("Missing CA signing certificate");
        }

        byte[] bytes = Utils.base64decode(cert);
        logger.debug("CertificateAuthority: size: " + bytes.length + " bytes");

        try {
            return new X509CertImpl(bytes);

        } catch (CertificateException e) {
            logger.error("Unable to parse CA signing cert: " + e.getMessage(), e);
            throw new EBaseException(e);
        }
    }

    public org.mozilla.jss.crypto.X509Certificate getCaX509Cert() {
        return mCaX509Cert;
    }

    public String[] getCASigningAlgorithms() {
        if (mCASigningAlgorithms != null)
            return mCASigningAlgorithms;

        if (mCaCert == null)
            return null; // CA not inited yet.
        X509Key caPubKey = null;

        try {
            caPubKey = (X509Key) mCaCert.get(X509CertImpl.PUBLIC_KEY);
        } catch (CertificateParsingException e) {
        }
        if (caPubKey == null)
            return null; // something seriously wrong.
        AlgorithmId alg = caPubKey.getAlgorithmId();

        if (alg == null)
            return null; // something seriously wrong.
        mCASigningAlgorithms = AlgorithmId.getSigningAlgorithms(alg);
        if (mCASigningAlgorithms == null) {
            logger.warn(
                    "CA - no signing algorithms for " + alg.getName());
        } else {
            logger.debug(
                    "CA First signing algorithm is " + mCASigningAlgorithms[0]);
        }

        return mCASigningAlgorithms;
    }

    //////////
    // Initialization routines.
    //

    /**
     * init CA signing unit & cert chain.
     */
    synchronized void initSigUnit() throws EBaseException {

        logger.debug("CertificateAuthority: initializing signing unit for " + mName);

        mSigningUnit = new SigningUnit();
        IConfigStore caSigningCfg = mConfig.getSubStore(PROP_SIGNING_SUBSTORE);

        try {
            String caSigningCertStr = caSigningCfg.getString("cert", "");
            if (caSigningCertStr.equals("")) {
                logger.debug("CertificateAuthority: CA signing cert not found");

            } else {
                logger.debug("CertificateAuthority: CA signing cert: " + caSigningCertStr);

                byte[] bytes = Utils.base64decode(caSigningCertStr);
                logger.debug("CertificateAuthority: size: " + bytes.length + " bytes");

                mCaCert = new X509CertImpl(bytes);

                // this ensures the isserDN and subjectDN have the same encoding
                // as that of the CA signing cert
                mSubjectObj = mCaCert.getSubjectObj();
                logger.debug("CertificateAuthority: subject DN: " + mSubjectObj);

                // this mIssuerObj is the "issuerDN" obj for the certs this CA
                // issues, NOT necessarily the isserDN obj of the CA signing cert
                mIssuerObj = new CertificateIssuerName((X500Name)mSubjectObj.get(CertificateIssuerName.DN_NAME));
                logger.debug("CertificateAuthority: issuer DN: " + mIssuerObj);
            }

        } catch (CertificateException e) {
            logger.error("Unable to initialize signing unit: " + e.getMessage(), e);
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN", e.toString()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED", e.toString()), e);

        } catch (IOException e) {
            logger.error("Unable to initialize signing unit: " + e.getMessage(), e);
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN", e.toString()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED", e.toString()), e);
        }

        mSigningUnit.init(this, caSigningCfg, mNickname);
        hasKeys = true;
        signingUnitException = null;
        logger.debug("CA signing unit inited");

        try {
            // for identrus
            IConfigStore CrlStore = mConfig.getSubStore(PROP_CRL_SIGNING_SUBSTORE);

            if (isHostAuthority() && CrlStore != null && CrlStore.size() > 0) {
                mCRLSigningUnit = new SigningUnit();
                mCRLSigningUnit.init(this, mConfig.getSubStore(PROP_CRL_SIGNING_SUBSTORE));
            } else {
                mCRLSigningUnit = mSigningUnit;
            }

            // init cert chain
            CryptoManager manager = CryptoManager.getInstance();

            int caChainNum = caSigningCfg.getInteger(PROP_CA_CHAIN_NUM, 0);
            logger.debug("CertificateAuthority: cachainNum: " + caChainNum);

            if (caChainNum > 0) {

                logger.debug("CertificateAuthority: create cert chain from files:");

                IConfigStore chainStore = caSigningCfg.getSubStore(PROP_CA_CHAIN);

                if (chainStore == null) {
                    log(ILogger.LL_FAILURE,
                            CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN",
                                    "ca cert chain config error"));
                    throw new ECAException(
                            CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED",
                                    "ca cert chain config error"));
                }

                java.security.cert.X509Certificate[] implchain =
                        new java.security.cert.X509Certificate[caChainNum];

                for (int i = 0; i < caChainNum; i++) {
                    String subtreeName = PROP_CA_CERT + i;

                    // cert file name must be full path
                    String certFileName = chainStore.getString(subtreeName, null);
                    logger.debug(" - file: " + certFileName);

                    if ((certFileName == null) || certFileName.equals("")) {
                        log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN", "cert file config error"));
                        throw new ECAException(
                                CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED",
                                        "cert file config error"));
                    }

                    byte[] b64Bytes = getCertFromFile(certFileName);
                    String b64String = new String(b64Bytes);
                    byte[] certBytes = KeyCertUtil.convertB64EToByteArray(b64String);

                    implchain[i] = new X509CertImpl(certBytes);
                }

                mCACertChain = new CertificateChain(implchain);

            } else {

                logger.debug("CertificateAuthority: create cert chain from certs in NSS database");

                org.mozilla.jss.crypto.X509Certificate caCert = mSigningUnit.getCert();
                logger.debug("CertificateAuthority: CA cert: " + caCert.getSubjectDN());

                org.mozilla.jss.crypto.X509Certificate[] chain =
                        manager.buildCertificateChain(caCert);

                // do this in case other subsyss expect a X509CertImpl
                java.security.cert.X509Certificate[] implchain =
                        new java.security.cert.X509Certificate[chain.length];

                for (int i = 0; i < chain.length; i++) {
                    implchain[i] = new X509CertImpl(chain[i].getEncoded());
                    logger.debug("CertificateAuthority: CA cert: " + caCert.getSubjectDN());
                }

                mCACertChain = new CertificateChain(implchain);
            }

            logger.debug("CertificateAuthority: cert chain created");

            IConfigStore OCSPStore = mConfig.getSubStore(PROP_OCSP_SIGNING_SUBSTORE);

            if (isHostAuthority() && OCSPStore != null && OCSPStore.size() > 0) {
                mOCSPSigningUnit = new SigningUnit();
                mOCSPSigningUnit.init(this, mConfig.getSubStore(PROP_OCSP_SIGNING_SUBSTORE));
                logger.debug("Separate OCSP signing unit inited");
            } else {
                mOCSPSigningUnit = mSigningUnit;
                logger.debug("Shared OCSP signing unit inited");
            }

            org.mozilla.jss.crypto.X509Certificate[] ocspChain =
                    manager.buildCertificateChain(mOCSPSigningUnit.getCert());
            // do this in case other subsyss expect a X509CertImpl
            java.security.cert.X509Certificate[] ocspImplchain =
                    new java.security.cert.X509Certificate[ocspChain.length];

            for (int i = 0; i < ocspChain.length; i++) {
                ocspImplchain[i] = new X509CertImpl(ocspChain[i].getEncoded());
            }
            mOCSPCertChain = new CertificateChain(ocspImplchain);
            logger.debug("in init - got OCSP chain from JSS.");

            mCaX509Cert = mSigningUnit.getCert();
            mCaCert = new X509CertImpl(mCaX509Cert.getEncoded());
            getCASigningAlgorithms();
            mSubjectObj = mCaCert.getSubjectObj();
            if (mSubjectObj != null) {
                // this ensures the isserDN and subjectDN have the same encoding
                // as that of the CA signing cert
                logger.debug("CertificateAuthority: initSigUnit - setting mIssuerObj and mSubjectObj");
                // this mIssuerObj is the "issuerDN" obj for the certs this CA
                // issues, NOT necessarily the isserDN obj of the CA signing cert
                // unless the CA is self-signed
                mIssuerObj =
                        new CertificateIssuerName((X500Name)mSubjectObj.get(CertificateIssuerName.DN_NAME));
            }
            mName = (X500Name) mCaCert.getSubjectDN();

            mCRLX509Cert = mCRLSigningUnit.getCert();
            mCRLCert = new X509CertImpl(mCRLX509Cert.getEncoded());
            mCRLName = (X500Name) mCRLCert.getSubjectDN();

            mOCSPX509Cert = mOCSPSigningUnit.getCert();
            mOCSPNickname = mOCSPSigningUnit.getNickname();
            mOCSPCert = new X509CertImpl(mOCSPX509Cert.getEncoded());
            mOCSPName = (X500Name) mOCSPCert.getSubjectDN();
            mNickname = mSigningUnit.getNickname();
            logger.debug("in init - got CA name " + mName);

        } catch (NotInitializedException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_SIGNING", e.toString()));
            throw new ECAException(CMS.getUserMessage("CMS_CA_CRYPTO_NOT_INITIALIZED"), e);

        } catch (CertificateException e) {
            logger.error("Unable to build cert chain: " + e.getMessage(), e);
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN", e.toString()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED", e.toString()), e);

        } catch (FileNotFoundException e) {
            logger.error("Unable to build cert chain: " + e.getMessage(), e);
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN", e.toString()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED", e.toString()), e);

        } catch (IOException e) {
            logger.error("Unable to build cert chain: " + e.getMessage(), e);
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN", e.toString()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED", e.toString()), e);

        } catch (TokenException e) {
            logger.error("Unable to build cert chain: " + e.getMessage(), e);
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_CHAIN", e.toString()));
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_BUILD_CA_CHAIN_FAILED", e.toString()), e);
        }

        generateSigningInfoAuditEvents();
    }

    /**
     * read ca cert from path, converts and bytes
     */
    byte[] getCertFromFile(String path)
            throws FileNotFoundException, IOException {

        File file = new File(path);
        Long l = Long.valueOf(file.length());
        byte[] b = new byte[l.intValue()];
        FileInputStream in = null;
        try {
            in = new FileInputStream(path);
            in.read(b);
        } finally {
            if (in != null)
                in.close();
        }
        return b;
    }

    /**
     * init default cert attributes.
     */
    private void initDefCaAttrs()
            throws EBaseException {
        int version = mConfig.getInteger(PROP_X509CERT_VERSION,
                CertificateVersion.V3);

        if (version != CertificateVersion.V1 &&
                version != CertificateVersion.V3) {
            throw new ECAException(
                    CMS.getUserMessage("CMS_CA_X509CERT_VERSION_NOT_SUPPORTED"));
        }
        try {
            mDefaultCertVersion = new CertificateVersion(version - 1);
        } catch (IOException e) {
            // should never occur.
        }

        int validity_in_days = mConfig.getInteger(PROP_DEF_VALIDITY, 2 * 365);

        mDefaultValidity = validity_in_days * DAY; // days in config file.

        mEnablePastCATime =
                mConfig.getBoolean(PROP_ENABLE_PAST_CATIME, false);
        mEnableOCSP =
                mConfig.getBoolean(PROP_ENABLE_OCSP, true);

        String fs = mConfig.getString(PROP_FAST_SIGNING, "");

        if (fs.equals("enabled") || fs.equals("enable")) {
            mFastSigning = FASTSIGNING_ENABLED;
        } else {
            mFastSigning = FASTSIGNING_DISABLED;
        }

    }

    /**
     * init cert & crl database
     */
    private void initCertDatabase()
            throws EBaseException {
        if (!isHostAuthority()) {
            mCertRepot = hostCA.mCertRepot;
            return;
        }

        int certdb_inc = mConfig.getInteger(PROP_CERTDB_INC, 5);

        String certReposDN = mConfig.getString(PROP_CERT_REPOS_DN, null);

        if (certReposDN == null) {
            certReposDN = "ou=certificateRepository, ou=" + getId() +
                    ", " + getDBSubsystem().getBaseDN();
        }
        String reposDN = mConfig.getString(PROP_REPOS_DN, null);

        if (reposDN == null) {
            reposDN = "ou=certificateRepository, ou=" + getId() +
                    ", " + getDBSubsystem().getBaseDN();
        }

        int transitMaxRecords = mConfig.getInteger(PROP_CERTDB_TRANS_MAXRECORDS, 1000000);
        int transitRecordPageSize = mConfig.getInteger(PROP_CERTDB_TRANS_PAGESIZE, 200);

        mCertRepot = new CertificateRepository(
                    DBSubsystem.getInstance(),
                    certReposDN, certdb_inc, reposDN);

        mCertRepot.setTransitMaxRecords(transitMaxRecords);
        mCertRepot.setTransitRecordPageSize(transitRecordPageSize);

        logger.debug("Cert Repot inited");
    }

    /**
     * init cert & crl database
     */
    private void initCrlDatabase()
            throws EBaseException {
        if (!isHostAuthority()) {
            mCRLRepot = hostCA.mCRLRepot;
            return;
        }

        int crldb_inc = mConfig.getInteger(PROP_CRLDB_INC, 5);

        mCRLRepot = new CRLRepository(
                    DBSubsystem.getInstance(),
                    crldb_inc,
                    "ou=crlIssuingPoints, ou=" + getId() + ", " +
                            getDBSubsystem().getBaseDN());
        logger.debug("CRL Repot inited");
    }

    private void startPublish()
            throws EBaseException {
        //xxx Note that CMS411 only support ca cert publishing to ldap
        // if ldap publishing is not enabled while publishing isenabled
        // there will be a lot of problem.
        try {
            if (mPublisherProcessor.isCertPublishingEnabled()) {
                mPublisherProcessor.publishCACert(mCaCert);
                logger.debug("published ca cert");
            }
        } catch (ELdapException e) {
            // exception not thrown - not seen as a fatal error.
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_PUBLISH", e.toString()));
        }
    }

    /**
     * init publishing
     */
    private void initPublish()
            throws EBaseException {
        if (!isHostAuthority()) {
            mPublisherProcessor = hostCA.mPublisherProcessor;
            return;
        }

        IConfigStore c = null;

        try {
            c = mConfig.getSubStore(PROP_PUBLISH_SUBSTORE);
            if (c != null && c.size() > 0) {
                mPublisherProcessor = new PublisherProcessor(
                            getId() + "pp");
                mPublisherProcessor.init(this, c);
                logger.debug("Publishing inited");
            } else {
                log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_PUBLISH"));
                throw new ECAException(
                        CMS.getUserMessage("CMS_CA_INIT_PUBLISH_MODULE_FAILED"));
            }

        } catch (ELdapException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_ERROR_PUBLISH_MODULE", e.toString()));
            //throw new ECAException(
            //	CAResources.INIT_PUBLISH_MODULE_FAILED, e);
        }
    }

    private void initMiscellaneousListeners() {
        IConfigStore lc = null;
        IConfigStore implc = null;
        IConfigStore instc = null;

        mListenerPlugins = new Hashtable<String, ListenerPlugin>();
        try {
            // Get list of listener implementations
            lc = mConfig.getSubStore(PROP_LISTENER_SUBSTORE);
            if (lc != null) {

                implc = lc.getSubStore(PROP_IMPL);
                Enumeration<String> names = implc.getSubStoreNames();

                while (names.hasMoreElements()) {
                    String id = names.nextElement();

                    logger.debug("registering listener impl: " + id);
                    String cl = implc.getString(id + "." + PROP_CLASS);

                    ListenerPlugin plugin = new ListenerPlugin(id, cl);

                    mListenerPlugins.put(id, plugin);
                }

                instc = lc.getSubStore(PROP_INSTANCE);
                Enumeration<String> instances = instc.getSubStoreNames();

                while (instances.hasMoreElements()) {
                    String id = instances.nextElement();

                    logger.debug("registering listener instance: " + id);
                    IConfigStore iConfig = instc.getSubStore(id);
                    String implName = instc.getString(id + "." + PROP_PLUGIN);
                    ListenerPlugin plugin = mListenerPlugins.get(implName);

                    if (plugin == null) {
                        log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_ERROR_LISTENER", implName));
                        throw new Exception("Cannot initialize");
                    }
                    String className = plugin.getClassPath();

                    try {
                        IRequestListener listener = null;

                        listener = (IRequestListener)
                                Class.forName(className).newInstance();

                        //listener.init(id, implName, iConfig);
                        listener.init(this, iConfig);
                        // registerRequestListener(id, (IRequestListener) listener);
                        //log(ILogger.LL_INFO,
                        //   "Listener instance " + id + " added");

                    } catch (Exception e) {
                        logger.error("Unable to add listener instance: " + e, e);
                        log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_INIT_LISTENER", id, e.toString()));
                        throw e;
                    }
                }

            }

        } catch (Exception e) {
            log(ILogger.LL_INFO, CMS.getLogMessage("CMSCORE_CA_CA_FAILED_LISTENER", e.toString()));
        }

    }

    /**
     * init notification related listeners
     */
    private void initNotificationListeners() {
        IConfigStore nc = null;

        try {
            nc = mConfig.getSubStore(PROP_NOTIFY_SUBSTORE);
            if (nc != null && nc.size() > 0) {
                // Initialize Certificate Issued notification listener

                String certificateIssuedListenerClassName =
                        nc.getString("certificateIssuedListenerClassName",
                                "com.netscape.cms.listeners.CertificateIssuedListener");

                try {
                    mCertIssuedListener =
                            (IRequestListener) Class.forName(certificateIssuedListenerClassName).newInstance();
                    mCertIssuedListener.init(this, nc);
                } catch (Exception e1) {
                    log(ILogger.LL_FAILURE,
                            CMS.getLogMessage("CMSCORE_CA_CA_REGISTER_LISTENER", certificateIssuedListenerClassName));
                }

                // Initialize Revoke Request notification listener

                String certificateRevokedListenerClassName =
                        nc.getString("certificateIssuedListenerClassName",
                                "com.netscape.cms.listeners.CertificateRevokedListener");

                try {
                    mCertRevokedListener =
                            (IRequestListener) Class.forName(certificateRevokedListenerClassName).newInstance();
                    mCertRevokedListener.init(this, nc);
                } catch (Exception e1) {
                    log(ILogger.LL_FAILURE,
                            CMS.getLogMessage("CMSCORE_CA_CA_REGISTER_LISTENER", certificateRevokedListenerClassName));
                }

                // Initialize Request In Queue notification listener
                String requestInQListenerClassName =
                        nc.getString("certificateIssuedListenerClassName",
                                "com.netscape.cms.listeners.RequestInQListener");

                try {
                    mReqInQListener = (IRequestListener) Class.forName(requestInQListenerClassName).newInstance();
                    mReqInQListener.init(this, nc);
                } catch (Exception e1) {
                    log(ILogger.LL_FAILURE,
                            CMS.getLogMessage("CMSCORE_CA_CA_REGISTER_REQ_LISTENER", requestInQListenerClassName));
                }

            } else {
                log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NOTIFY_NONE"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NOTIFY_FAILED"));
            //			throw e;
        }
    }

    /**
     * initialize request queue components
     */
    private void initRequestQueue()
            throws EBaseException {
        if (!isHostAuthority()) {
            mPolicy = hostCA.mPolicy;
            mService = hostCA.mService;
            mNotify = hostCA.mNotify;
            mPNotify = hostCA.mPNotify;
            mRequestQueue = hostCA.mRequestQueue;
            return;
        }

        CMSEngine engine = CMS.getCMSEngine();
        mPolicy = new CAPolicy();
        mPolicy.init(this, mConfig.getSubStore(PROP_POLICY));
        logger.debug("CA policy inited");
        mService = new CAService(this);
        logger.debug("CA service inited");

        mNotify = new ARequestNotifier(this);
        logger.debug("CA notifier inited");
        mPNotify = new ARequestNotifier();
        logger.debug("CA pending notifier inited");

        // instantiate CA request queue.
        try {
            int reqdb_inc = mConfig.getInteger("reqdbInc", 5);

            RequestSubsystem reqSub = (RequestSubsystem) engine.getSubsystem(RequestSubsystem.ID);
            mRequestQueue = reqSub.getRequestQueue(
                            getId(), reqdb_inc, mPolicy, mService, mNotify, mPNotify);
        } catch (EBaseException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_QUEUE_FAILED", e.toString()));
            throw e;
        }

        // init request scheduler if configured
        String schedulerClass =
                mConfig.getString("requestSchedulerClass", null);

        if (schedulerClass != null) {
            try {
                IRequestScheduler scheduler = (IRequestScheduler)
                        Class.forName(schedulerClass).newInstance();

                mRequestQueue.setRequestScheduler(scheduler);
            } catch (Exception e) {
                // do nothing here
            }
        }
    }

    /*
     private void startCRL()
     throws EBaseException
     {
     Enumeration e = mCRLIssuePoints.keys();
     while (e.hasMoreElements()) {
     CRLIssuingPoint cp = (CRLIssuingPoint)
     mCRLIssuePoints.get(e.nextElement());
     cp.startup();
     }
     }
     */

    /**
     * initialize CRL
     */
    @SuppressWarnings("unchecked")
    private void initCRL()
            throws EBaseException {
        if (!isHostAuthority()) {
            mCRLIssuePoints = hostCA.mCRLIssuePoints;
            mMasterCRLIssuePoint = hostCA.mMasterCRLIssuePoint;
            return;
        }
        IConfigStore crlConfig = mConfig.getSubStore(PROP_CRL_SUBSTORE);

        if ((crlConfig == null) || (crlConfig.size() <= 0)) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_MASTER_CRL"));
            //throw new ECAException(CAResources.NO_CONFIG_FOR_MASTER_CRL);
            return;
        }
        Enumeration<String> issuePointIdEnum = crlConfig.getSubStoreNames();

        if (issuePointIdEnum == null || !issuePointIdEnum.hasMoreElements()) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_MASTER_CRL_SUBSTORE"));
            //throw new ECAException(CAResources.NO_CONFIG_FOR_MASTER_CRL);
            return;
        }

        // a Master/full crl must exist.
        CRLIssuingPoint masterCRLIssuePoint = null;

        while (issuePointIdEnum.hasMoreElements()) {
            String issuePointId = issuePointIdEnum.nextElement();

            logger.debug("initializing crl issue point " + issuePointId);
            IConfigStore issuePointConfig = null;
            String issuePointClassName = null;
            Class<CRLIssuingPoint> issuePointClass = null;
            CRLIssuingPoint issuePoint = null;

            try {
                issuePointConfig = crlConfig.getSubStore(issuePointId);
                issuePointClassName = issuePointConfig.getString(PROP_CLASS);
                issuePointClass = (Class<CRLIssuingPoint>) Class.forName(issuePointClassName);
                issuePoint = issuePointClass.newInstance();
                issuePoint.init(this, issuePointId, issuePointConfig);
                mCRLIssuePoints.put(issuePointId, issuePoint);

                if (masterCRLIssuePoint == null &&
                        issuePointId.equals(PROP_MASTER_CRL))
                    masterCRLIssuePoint = issuePoint;

            } catch (ClassNotFoundException e) {
                throw new ECAException(
                        CMS.getUserMessage("CMS_CA_CRL_ISSUING_POINT_INIT_FAILED",
                                issuePointId, e.toString()));
            } catch (InstantiationException e) {
                throw new ECAException(
                        CMS.getUserMessage("CMS_CA_CRL_ISSUING_POINT_INIT_FAILED",
                                issuePointId, e.toString()));
            } catch (IllegalAccessException e) {
                throw new ECAException(
                        CMS.getUserMessage("CMS_CA_CRL_ISSUING_POINT_INIT_FAILED",
                                issuePointId, e.toString()));
            }
        }

        mMasterCRLIssuePoint = masterCRLIssuePoint;

        /*
         if (mMasterCRLIssuePoint == null) {
         log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_NO_FULL_CRL", PROP_MASTER_CRL));
         throw new ECAException(CAResources.NO_CONFIG_FOR_MASTER_CRL);
         }
         */
        log(ILogger.LL_INFO, "CRL Issuing Points inited");
    }

    public String getOfficialName() {
        return OFFICIAL_NAME;
    }

    public long getNumOCSPRequest() {
        return mNumOCSPRequest;
    }

    public long getOCSPRequestTotalTime() {
        return mTotalTime;
    }

    public long getOCSPTotalData() {
        return mTotalData;
    }

    public long getOCSPTotalSignTime() {
        return mSignTime;
    }

    public long getOCSPTotalLookupTime() {
        return mLookupTime;
    }

    public ResponderID getResponderIDByName() {
        try {
            X500Name name = getOCSPX500Name();
            Name.Template nameTemplate = new Name.Template();

            return new NameID((Name) nameTemplate.decode(
                        new ByteArrayInputStream(name.getEncoded())));
        } catch (IOException e) {
            return null;
        } catch (InvalidBERException e) {
            return null;
        }
    }

    public ResponderID getResponderIDByHash() {

        /*
         KeyHash ::= OCTET STRING --SHA-1 hash of responder's public key
         --(excluding the tag and length fields)
         */
        PublicKey publicKey = getOCSPSigningUnit().getPublicKey();
        MessageDigest md = null;

        try {
            md = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        md.update(publicKey.getEncoded());
        byte digested[] = md.digest();

        return new KeyHashID(new OCTET_STRING(digested));
    }

    /**
     * Process OCSPRequest.
     */
    public OCSPResponse validate(OCSPRequest request)
            throws EBaseException {

        if (!mEnableOCSP) {
            logger.debug("CertificateAuthority: OCSP service disabled");
            throw new EBaseException("OCSP service disabled");
        }

        TBSRequest tbsReq = request.getTBSRequest();
        if (tbsReq.getRequestCount() == 0) {
            logger.error("CertificateAuthority: No request found");
            log(ILogger.LL_FAILURE, CMS.getLogMessage("OCSP_REQUEST_FAILURE", "No Request Found"));
            throw new EBaseException("OCSP request is empty");
        }

        /* An OCSP request can contain CertIDs for certificates
         * issued by different CAs, but each SingleResponse is valid
         * only if the combined response was signed by its issuer or
         * an authorised OCSP signing delegate.
         *
         * Even though it is silly to send an OCSP request
         * asking about certs issued by different CAs, we must
         * employ some heuristic to deal with this case. Our
         * heuristic is:
         *
         * 0. If caMap contains no CAs, then lightweight CAs are not
         *    enabled.  There is only one CA, and 'this' is it.  Go
         *    straight to validation.
         *
         * 1. Find the issuer of the cert identified by the first
         *    CertID in the request.
         *
         * 2. If this CA is *not* the issuer, look up the issuer
         *    by its DN in the caMap.  If not found, fail.  If
         *    found, dispatch to its 'validate' method.  Otherwise
         *    continue.
         *
         * 3. If this CA is NOT the issuing CA, we locate the
         *    issuing CA and dispatch to its 'validate' method.
         *    Otherwise, we move forward to generate and sign the
         *    aggregate OCSP response.
         */
        CMSEngine engine = CMS.getCMSEngine();
        ICertificateAuthority ocspCA = this;
        if (caMap.size() > 0 && tbsReq.getRequestCount() > 0) {
            Request req = tbsReq.getRequestAt(0);
            BigInteger serialNo = req.getCertID().getSerialNumber();
            X509CertImpl cert = mCertRepot.getX509Certificate(serialNo);
            X500Name certIssuerDN = (X500Name) cert.getIssuerDN();
            ocspCA = getCA(certIssuerDN);
        }

        if (ocspCA == null) {
            logger.error("CertificateAuthority: Could not locate issuing CA");
            throw new CANotFoundException("Could not locate issuing CA");
        }

        if (ocspCA != this)
            return ((IOCSPService) ocspCA).validate(request);

        logger.debug("CertificateAuthority: validating OCSP request");

        mNumOCSPRequest++;
        IStatsSubsystem statsSub = (IStatsSubsystem) engine.getSubsystem(IStatsSubsystem.ID);
        long startTime = new Date().getTime();

        try {
            //log(ILogger.LL_INFO, "start OCSP request");

            // (3) look into database to check the
            //     certificate's status
            Vector<SingleResponse> singleResponses = new Vector<SingleResponse>();

            if (statsSub != null) {
                statsSub.startTiming("lookup");
            }

            long lookupStartTime = new Date().getTime();

            for (int i = 0; i < tbsReq.getRequestCount(); i++) {
                Request req = tbsReq.getRequestAt(i);
                SingleResponse sr = processRequest(req);
                singleResponses.addElement(sr);
            }

            long lookupEndTime = new Date().getTime();
            mLookupTime += lookupEndTime - lookupStartTime;

            if (statsSub != null) {
                statsSub.endTiming("lookup");
            }

            if (statsSub != null) {
                statsSub.startTiming("build_response");
            }

            SingleResponse res[] = new SingleResponse[singleResponses.size()];
            singleResponses.copyInto(res);

            ResponderID rid = null;

            if (mByName) {
                if (mResponderIDByName == null) {
                    mResponderIDByName = getResponderIDByName();
                }
                rid = mResponderIDByName;
            } else {
                if (mResponderIDByHash == null) {
                    mResponderIDByHash = getResponderIDByHash();
                }
                rid = mResponderIDByHash;
            }

            Extension nonce[] = null;

            for (int j = 0; j < tbsReq.getExtensionsCount(); j++) {
                Extension thisExt = tbsReq.getRequestExtensionAt(j);

                if (thisExt.getExtnId().equals(OCSP_NONCE)) {
                    nonce = new Extension[1];
                    nonce[0] = thisExt;
                }
            }

            ResponseData rd = new ResponseData(rid,
                    new GeneralizedTime(new Date()), res, nonce);

            if (statsSub != null) {
                statsSub.endTiming("build_response");
            }

            if (statsSub != null) {
                statsSub.startTiming("signing");
            }

            long signStartTime = new Date().getTime();

            BasicOCSPResponse basicRes = sign(rd);

            long signEndTime = new Date().getTime();
            mSignTime += signEndTime - signStartTime;

            if (statsSub != null) {
                statsSub.endTiming("signing");
            }

            OCSPResponse response = new OCSPResponse(
                    OCSPResponseStatus.SUCCESSFUL,
                    new ResponseBytes(ResponseBytes.OCSP_BASIC,
                            new OCTET_STRING(ASN1Util.encode(basicRes))));

            //log(ILogger.LL_INFO, "done OCSP request");
            long endTime = new Date().getTime();
            mTotalTime += endTime - startTime;

            return response;

        } catch (EBaseException e) {
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_REQUEST", e.toString()));
            throw e;
        }
    }

    private BasicOCSPResponse sign(ResponseData rd) throws EBaseException {
        ensureReady();
        try (DerOutputStream out = new DerOutputStream()) {
            DerOutputStream tmp = new DerOutputStream();

            String algname = mOCSPSigningUnit.getDefaultAlgorithm();

            byte rd_data[] = ASN1Util.encode(rd);
            if (rd_data != null) {
                mTotalData += rd_data.length;
            }
            rd.encode(tmp);
            AlgorithmId.get(algname).encode(tmp);
            logger.debug("adding signature");
            byte[] signature = mOCSPSigningUnit.sign(rd_data, algname);

            tmp.putBitString(signature);
            // optional, put the certificate chains in also

            DerOutputStream tmpChain = new DerOutputStream();
            DerOutputStream tmp1 = new DerOutputStream();
            java.security.cert.X509Certificate chains[] =
                    mOCSPCertChain.getChain();

            for (int i = 0; i < chains.length; i++) {
                tmpChain.putDerValue(new DerValue(chains[i].getEncoded()));
            }
            tmp1.write(DerValue.tag_Sequence, tmpChain);
            tmp.write(DerValue.createTag(DerValue.TAG_CONTEXT, true, (byte) 0),
                    tmp1);

            out.write(DerValue.tag_Sequence, tmp);

            BasicOCSPResponse response = new BasicOCSPResponse(out.toByteArray());

            return response;
        } catch (Exception e) {
            e.printStackTrace();
            // error e
            log(ILogger.LL_FAILURE, CMS.getLogMessage("CMSCORE_CA_CA_OCSP_SIGN", e.toString()));
            throw new EBaseException(e.toString());
        }
    }

    private SingleResponse processRequest(Request req) {

        CertID cid = req.getCertID();
        INTEGER serialNo = cid.getSerialNumber();
        logger.debug("CertificateAuthority: processing request for cert 0x" + serialNo.toString(16));

        CertStatus certStatus = null;
        GeneralizedTime thisUpdate = new GeneralizedTime(new Date());

        byte[] nameHash = null;
        String digestName = cid.getDigestName();
        if (digestName != null) {
            try {
                MessageDigest md = MessageDigest.getInstance(digestName);
                nameHash = md.digest(mName.getEncoded());
            } catch (NoSuchAlgorithmException | IOException e) {
            }
        }
        if (!Arrays.equals(cid.getIssuerNameHash().toByteArray(), nameHash)) {
            // issuer of cert is not this CA (or we couldn't work
            // out whether it is or not due to unknown hash alg);
            // do not return status information for this cert
            return new SingleResponse(cid, new UnknownInfo(), thisUpdate, null);
        }

        boolean ocspUseCache = true;

        try {
            /* enable OCSP cache by default */
            ocspUseCache = mConfig.getBoolean("ocspUseCache", false);
        } catch (EBaseException e) {
        }

        if (ocspUseCache) {
            String issuingPointId = PROP_MASTER_CRL;

            try {
                issuingPointId = mConfig.getString(
                            "ocspUseCacheIssuingPointId", PROP_MASTER_CRL);

            } catch (EBaseException e) {
            }
            CRLIssuingPoint point = (CRLIssuingPoint)
                    getCRLIssuingPoint(issuingPointId);

            /* set nextUpdate to the nextUpdate time of the CRL */
            GeneralizedTime nextUpdate = null;
            Date crlNextUpdate = point.getNextUpdate();
            if (crlNextUpdate != null)
                nextUpdate = new GeneralizedTime(crlNextUpdate);

            if (point.isCRLCacheEnabled()) {
                // only do this if cache is enabled
                BigInteger sno = new BigInteger(serialNo.toString());
                boolean checkDeltaCache = false;
                boolean includeExpiredCerts = false;

                try {
                    checkDeltaCache = mConfig.getBoolean("ocspUseCacheCheckDeltaCache", false);
                } catch (EBaseException e) {
                }
                try {
                    includeExpiredCerts = mConfig.getBoolean("ocspUseCacheIncludeExpiredCerts", false);
                } catch (EBaseException e) {
                }
                Date revokedOn = point.getRevocationDateFromCache(
                        sno, checkDeltaCache, includeExpiredCerts);

                if (revokedOn == null) {
                    certStatus = new GoodInfo();
                } else {
                    certStatus = new RevokedInfo(new GeneralizedTime(revokedOn));
                }
                return new SingleResponse(cid, certStatus, thisUpdate, nextUpdate);
            }
        }

        try {
            ICertRecord rec = mCertRepot.readCertificateRecord(serialNo);
            String status = rec.getStatus();

            if (status == null) {
                certStatus = new UnknownInfo();
            } else if (status.equals(CertRecord.STATUS_VALID)) {
                certStatus = new GoodInfo();
            } else if (status.equals(CertRecord.STATUS_INVALID)) {
                // not yet valid
                certStatus = new UnknownInfo();
            } else if (status.equals(CertRecord.STATUS_REVOKED)) {
                certStatus = new RevokedInfo(new GeneralizedTime(rec.getRevokedOn()));
            } else if (status.equals(CertRecord.STATUS_EXPIRED)) {
                certStatus = new UnknownInfo();
            } else if (status.equals(CertRecord.STATUS_REVOKED_EXPIRED)) {
                certStatus = new RevokedInfo(new GeneralizedTime(rec.getRevokedOn()));
            } else {
                certStatus = new UnknownInfo();
            }
        } catch (Exception e) {
            // not found
            certStatus = new UnknownInfo(); // not issued not all
        }

        return new SingleResponse(
            cid, certStatus, thisUpdate,
            /* We are not using a CRL cache for generating OCSP
             * responses, so there is no reasonable value for
             * nextUpdate. */
            null /* nextUpdate */);
    }

    /**
     * Enumerate all authorities (including host authority)
     */
    public List<ICertificateAuthority> getCAs() {
        List<ICertificateAuthority> cas = new ArrayList<>();
        synchronized (caMap) {
            for (ICertificateAuthority ca : caMap.values()) {
                cas.add(ca);
            }
        }
        return cas;
    }

    /**
     * Get authority by ID.
     *
     * @param aid The ID of the CA to retrieve, or null
     *             to retreive the host authority.
     *
     * @return the authority, or null if not found
     */
    public ICertificateAuthority getCA(AuthorityID aid) {
        return aid == null ? hostCA : caMap.get(aid);
    }

    public ICertificateAuthority getCA(X500Name dn) {
        for (ICertificateAuthority ca : getCAs()) {
            if (ca.getX500Name().equals(dn))
                return ca;
        }
        return null;
    }

    public AuthorityID getAuthorityID() {
        return authorityID;
    }

    public AuthorityID getAuthorityParentID() {
        return authorityParentID;
    }

    public String getAuthorityDescription() {
        return authorityDescription;
    }

    /**
     * Create a new lightweight authority.
     *
     * @param subjectDN Subject DN for new CA
     * @param parentAID ID of parent CA
     * @param description Optional string description of CA
     */
    public ICertificateAuthority createCA(
            IAuthToken authToken,
            String subjectDN, AuthorityID parentAID,
            String description)
            throws EBaseException {
        ICertificateAuthority parentCA = getCA(parentAID);
        if (parentCA == null)
            throw new CANotFoundException(
                "Parent CA \"" + parentAID + "\" does not exist");

        ICertificateAuthority ca = parentCA.createSubCA(
                authToken, subjectDN, description);
        caMap.put(ca.getAuthorityID(), ca);
        return ca;
    }

    private void ensureAuthorityDNAvailable(X500Name dn)
            throws IssuerUnavailableException {
        for (ICertificateAuthority ca : getCAs()) {
            if (ca.getX500Name().equals(dn))
                throw new IssuerUnavailableException(
                    "DN '" + dn + "' is used by an existing authority");
        }
    }

    /**
     * Create a new lightweight authority signed by this authority.
     *
     * This method DOES NOT add the new CA to caMap; it is the
     * caller's responsibility.
     */
    public ICertificateAuthority createSubCA(
            IAuthToken authToken,
            String subjectDN, String description)
            throws EBaseException {

        CMSEngine engine = CMS.getCMSEngine();
        ensureReady();

        // check requested DN
        X500Name subjectX500Name = null;
        try {
            subjectX500Name = new X500Name(subjectDN);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Invalid Subject DN: " + subjectDN);
        }
        ensureAuthorityDNAvailable(subjectX500Name);

        // generate authority ID and nickname
        AuthorityID aid = new AuthorityID();
        String aidString = aid.toString();
        String nickname = hostCA.getNickname() + " " + aidString;

        // build database entry
        String dn = "cn=" + aidString + "," + authorityBaseDN();
        logger.debug("createSubCA: DN = " + dn);
        String parentDNString = null;
        try {
            parentDNString = mName.toLdapDNString();
        } catch (IOException e) {
            throw new EBaseException("Failed to convert issuer DN to string: " + e);
        }

        String thisClone = engine.getEEHost() + ":" + engine.getEESSLPort();

        LDAPAttribute[] attrs = {
            new LDAPAttribute("objectclass", "authority"),
            new LDAPAttribute("cn", aidString),
            new LDAPAttribute("authorityID", aidString),
            new LDAPAttribute("authorityKeyNickname", nickname),
            new LDAPAttribute("authorityKeyHost", thisClone),
            new LDAPAttribute("authorityEnabled", "TRUE"),
            new LDAPAttribute("authorityDN", subjectDN),
            new LDAPAttribute("authorityParentDN", parentDNString)
        };
        LDAPAttributeSet attrSet = new LDAPAttributeSet(attrs);
        if (this.authorityID != null)
            attrSet.add(new LDAPAttribute(
                "authorityParentID", this.authorityID.toString()));
        if (description != null)
            attrSet.add(new LDAPAttribute("description", description));
        LDAPEntry ldapEntry = new LDAPEntry(dn, attrSet);

        addAuthorityEntry(aid, ldapEntry);

        X509CertImpl cert = null;

        try {
            // Generate signing key
            CryptoManager cryptoManager = CryptoManager.getInstance();
            // TODO read PROP_TOKEN_NAME config
            CryptoToken token = cryptoManager.getInternalKeyStorageToken();
            // TODO algorithm parameter
            KeyPairGenerator gen = token.getKeyPairGenerator(KeyPairAlgorithm.RSA);
            gen.initialize(2048);
            KeyPair keypair = gen.genKeyPair();
            PublicKey pub = keypair.getPublic();
            X509Key x509key = CryptoUtil.convertPublicKeyToX509Key(pub);

            // Create pkcs10 request
            logger.debug("createSubCA: creating pkcs10 request");
            PKCS10 pkcs10 = new PKCS10(x509key);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keypair.getPrivate());
            pkcs10.encodeAndSign(
                new X500Signer(signature, subjectX500Name));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pkcs10.print(new PrintStream(out));
            String pkcs10String = out.toString();

            // Sign certificate
            Locale locale = Locale.getDefault();
            String profileId = "caCACert";
            IProfileSubsystem ps = (IProfileSubsystem) engine.getSubsystem(IProfileSubsystem.ID);
            IProfile profile = ps.getProfile(profileId);
            ArgBlock argBlock = new ArgBlock();
            argBlock.set("cert_request_type", "pkcs10");
            argBlock.set("cert_request", pkcs10String);
            CertEnrollmentRequest certRequest =
                CertEnrollmentRequestFactory.create(argBlock, profile, locale);
            EnrollmentProcessor processor =
                new EnrollmentProcessor("createSubCA", locale);
            Map<String, Object> resultMap = processor.processEnrollment(
                certRequest, null, authorityID, null, authToken);
            IRequest requests[] = (IRequest[]) resultMap.get(CAProcessor.ARG_REQUESTS);
            IRequest request = requests[0];
            Integer result = request.getExtDataInInteger(IRequest.RESULT);
            if (result != null && !result.equals(IRequest.RES_SUCCESS))
                throw new EBaseException("createSubCA: certificate request submission resulted in error: " + result);
            RequestStatus requestStatus = request.getRequestStatus();
            if (requestStatus != RequestStatus.COMPLETE) {
                // The request did not complete.  Inference: something
                // incorrect in the request (e.g. profile constraint
                // violated).
                String msg = "Failed to issue CA certificate. Final status: " + requestStatus + ".";
                String errorMsg = request.getExtDataInString(IRequest.ERROR);
                if (errorMsg != null)
                    msg += " Additional info: " + errorMsg;
                throw new BadRequestDataException(msg);
            }

            // Add certificate to nssdb
            cert = request.getExtDataInCert(IEnrollProfile.REQUEST_ISSUED_CERT);
            cryptoManager.importCertPackage(cert.getEncoded(), nickname);
        } catch (Exception e) {
            // something went wrong; delete just-added entry
            logger.error("Error creating lightweight CA certificate: " + e.getMessage(), e);

            try {
                deleteAuthorityEntry(aid);
            } catch (ELdapException e2) {
                // we are about to throw ECAException, so just
                // log this error.
                logger.error("Error deleting new authority entry after failure during certificate generation: " + e2.getMessage(), e2);
            }
            if (e instanceof BadRequestDataException)
                throw (BadRequestDataException) e;  // re-throw
            else
                throw new ECAException("Error creating lightweight CA certificate: " + e, e);
        }

        CertificateAuthority ca = new CertificateAuthority(
            hostCA, subjectX500Name,
            aid, this.authorityID, cert.getSerialNumber(),
            nickname, Collections.singleton(thisClone),
            description, true);

        // Update authority record with serial of issued cert
        LDAPModificationSet mods = new LDAPModificationSet();
        mods.add(
            LDAPModification.REPLACE,
            new LDAPAttribute("authoritySerial", cert.getSerialNumber().toString()));
        ca.modifyAuthorityEntry(mods);

        return ca;
    }

    /**
     * Renew certificate of this CA.
     */
    public void renewAuthority(HttpServletRequest httpReq)
            throws EBaseException {

        CMSEngine engine = CMS.getCMSEngine();
        if (
            authorityParentID != null
            && !authorityParentID.equals(authorityID)
        ) {
            ICertificateAuthority issuer = getCA(authorityParentID);
            issuer.ensureReady();
        }

        IProfileSubsystem ps = (IProfileSubsystem) engine.getSubsystem(IProfileSubsystem.ID);
        IProfile profile = ps.getProfile("caManualRenewal");
        CertEnrollmentRequest req = CertEnrollmentRequestFactory.create(
            new ArgBlock(), profile, httpReq.getLocale());
        req.setSerialNum(new CertId(mCaCert.getSerialNumber()));
        RenewalProcessor processor =
            new RenewalProcessor("renewAuthority", httpReq.getLocale());
        Map<String, Object> resultMap =
            processor.processRenewal(req, httpReq, null);
        IRequest requests[] = (IRequest[]) resultMap.get(CAProcessor.ARG_REQUESTS);
        IRequest request = requests[0];
        Integer result = request.getExtDataInInteger(IRequest.RESULT);
        if (result != null && !result.equals(IRequest.RES_SUCCESS))
            throw new EBaseException("renewAuthority: certificate renewal submission resulted in error: " + result);
        RequestStatus requestStatus = request.getRequestStatus();
        if (requestStatus != RequestStatus.COMPLETE)
            throw new EBaseException("renewAuthority: certificate renewal did not complete; status: " + requestStatus);
        X509CertImpl cert = request.getExtDataInCert(IEnrollProfile.REQUEST_ISSUED_CERT);
        authoritySerial = cert.getSerialNumber();

        // Update authority record with serial of issued cert
        LDAPModificationSet mods = new LDAPModificationSet();
        mods.add(
            LDAPModification.REPLACE,
            new LDAPAttribute("authoritySerial", authoritySerial.toString()));
        modifyAuthorityEntry(mods);

        // update cert in NSSDB
        checkForNewerCert();
    }

    /**
     * Add an LDAP entry for the host authority.
     *
     * This method also sets the authorityID and authorityDescription
     * fields.
     *
     * It is the caller's responsibility to add the returned
     * AuthorityID to the caMap.
     */
    private AuthorityID addHostAuthorityEntry() throws EBaseException {
        if (!isHostAuthority())
            throw new EBaseException("Can only invoke from host CA");

        // generate authority ID
        AuthorityID aid = new AuthorityID();
        String aidString = aid.toString();

        // build database entry
        String dn = "cn=" + aidString + "," + authorityBaseDN();
        String dnString = null;
        try {
            dnString = mName.toLdapDNString();
        } catch (IOException e) {
            throw new EBaseException("Failed to convert issuer DN to string: " + e);
        }

        String desc = "Host authority";
        LDAPAttribute[] attrs = {
            new LDAPAttribute("objectclass", "authority"),
            new LDAPAttribute("cn", aidString),
            new LDAPAttribute("authorityID", aidString),
            new LDAPAttribute("authorityKeyNickname", getNickname()),
            new LDAPAttribute("authorityEnabled", "TRUE"),
            new LDAPAttribute("authorityDN", dnString),
            new LDAPAttribute("description", desc)
        };
        LDAPAttributeSet attrSet = new LDAPAttributeSet(attrs);
        LDAPEntry ldapEntry = new LDAPEntry(dn, attrSet);

        addAuthorityEntry(aid, ldapEntry);

        this.authorityID = aid;
        this.authorityDescription = desc;
        return aid;
    }

    private void addAuthorityEntry(AuthorityID aid, LDAPEntry entry)
            throws ELdapException {
        LDAPControl[] responseControls;
        LDAPConnection conn = dbFactory.getConn();
        synchronized (hostCA) {
            try {
                conn.add(entry, getCommitConstraints());
                responseControls = conn.getResponseControls();
            } catch (LDAPException e) {
                throw new ELdapException("addAuthorityEntry: failed to add entry", e);
            } finally {
                dbFactory.returnConn(conn);
            }
            postCommit(aid, responseControls);
        }
    }

    /**
     * Modify _this_ authority with the given modification set.
     */
    private void modifyAuthorityEntry(LDAPModificationSet mods)
            throws ELdapException {
        String dn = "cn=" + authorityID.toString() + "," + authorityBaseDN();
        LDAPControl[] responseControls;
        LDAPConnection conn = dbFactory.getConn();
        synchronized (hostCA) {
            try {
                conn.modify(dn, mods, getCommitConstraints());
                responseControls = conn.getResponseControls();
            } catch (LDAPException e) {
                throw new ELdapException("modifyAuthorityEntry: failed to modify entry", e);
            } finally {
                dbFactory.returnConn(conn);
            }
            postCommit(authorityID, responseControls);
        }
    }

    private LDAPConstraints getCommitConstraints() {
        String[] attrs = {"entryUSN", "nsUniqueId"};
        LDAPConstraints cons = new LDAPConstraints();
        LDAPPostReadControl control = new LDAPPostReadControl(true, attrs);
        cons.setServerControls(control);
        return cons;
    }

    /**
     * Post-commit processing of authority to track its entryUSN and nsUniqueId
     */
    private void postCommit(AuthorityID aid, LDAPControl[] responseControls) {
        LDAPPostReadControl control = (LDAPPostReadControl)
            LDAPUtil.getControl(LDAPPostReadControl.class, responseControls);
        LDAPEntry entry = control.getEntry();

        LDAPAttribute attr = entry.getAttribute("entryUSN");
        if (attr != null) {
            BigInteger entryUSN = new BigInteger(attr.getStringValueArray()[0]);
            entryUSNs.put(aid, entryUSN);
            logger.debug("postCommit: new entryUSN = " + entryUSN);
        }

        attr = entry.getAttribute("nsUniqueId");
        if (attr != null) {
            String nsUniqueId = attr.getStringValueArray()[0];
            nsUniqueIds.put(aid, nsUniqueId);
            logger.debug("postCommit: nsUniqueId = " + nsUniqueId);
        }
    }

    /**
     * Update lightweight authority attributes.
     *
     * Pass null values to exclude an attribute from the update.
     *
     * If a passed value matches the current value, it is excluded
     * from the update.
     *
     * To remove optional string values, pass the empty string.
     */
    public void modifyAuthority(Boolean enabled, String desc)
            throws EBaseException {
        if (isHostAuthority() && enabled != null && !enabled)
            throw new CATypeException("Cannot disable the host CA");

        LDAPModificationSet mods = new LDAPModificationSet();

        boolean nextEnabled = authorityEnabled;
        if (enabled != null && enabled.booleanValue() != authorityEnabled) {
            mods.add(
                LDAPModification.REPLACE,
                new LDAPAttribute("authorityEnabled", enabled ? "TRUE" : "FALSE"));
            nextEnabled = enabled;
        }

        String nextDesc = authorityDescription;
        if (desc != null) {
            if (!desc.isEmpty() && authorityDescription != null
                    && !desc.equals(authorityDescription)) {
                mods.add(
                    LDAPModification.REPLACE,
                    new LDAPAttribute("description", desc));
                nextDesc = desc;
            } else if (desc.isEmpty() && authorityDescription != null) {
                mods.add(
                    LDAPModification.DELETE,
                    new LDAPAttribute("description", authorityDescription));
                nextDesc = null;
            } else if (!desc.isEmpty() && authorityDescription == null) {
                mods.add(
                    LDAPModification.ADD,
                    new LDAPAttribute("description", desc));
                nextDesc = desc;
            }
        }

        if (mods.size() > 0) {
            modifyAuthorityEntry(mods);

            // update was successful; update CA's state
            authorityEnabled = nextEnabled;
            authorityDescription = nextDesc;
        }
    }

    /**
     * Add this instance to the authorityKeyHosts
     */
    void addInstanceToAuthorityKeyHosts() throws ELdapException {
        CMSEngine engine = CMS.getCMSEngine();
        String thisClone = engine.getEEHost() + ":" + engine.getEESSLPort();
        if (authorityKeyHosts.contains(thisClone)) {
            // already there; nothing to do
            return;
        }
        LDAPModificationSet mods = new LDAPModificationSet();
        mods.add(
            LDAPModification.ADD,
            new LDAPAttribute("authorityKeyHost", thisClone));
        modifyAuthorityEntry(mods);
        authorityKeyHosts.add(thisClone);
    }

    public synchronized void deleteAuthority(HttpServletRequest httpReq)
            throws EBaseException {
        if (isHostAuthority())
            throw new CATypeException("Cannot delete the host CA");

        if (authorityEnabled)
            throw new CAEnabledException("Must disable CA before deletion");

        boolean hasSubCAs = false;
        for (ICertificateAuthority ca : getCAs()) {
            AuthorityID parentAID = ca.getAuthorityParentID();
            if (parentAID != null && parentAID.equals(this.authorityID)) {
                hasSubCAs = true;
                break;
            }
        }
        if (hasSubCAs)
            throw new CANotLeafException("CA with sub-CAs cannot be deleted (delete sub-CAs first)");

        shutdown();

        revokeAuthority(httpReq);
        deleteAuthorityEntry(authorityID);
        deleteAuthorityNSSDB();
    }

    /** Revoke the authority's certificate
     *
     * TODO: revocation reason, invalidity date parameters
     */
    private void revokeAuthority(HttpServletRequest httpReq)
            throws EBaseException {
        logger.debug("revokeAuthority: checking serial " + authoritySerial);
        ICertRecord certRecord = mCertRepot.readCertificateRecord(authoritySerial);
        String curStatus = certRecord.getStatus();
        logger.debug("revokeAuthority: current cert status: " + curStatus);
        if (curStatus.equals(CertRecord.STATUS_REVOKED)
                || curStatus.equals(CertRecord.STATUS_REVOKED_EXPIRED)) {
            return;  // already revoked
        }

        logger.debug("revokeAuthority: revoking cert");
        RevocationProcessor processor = new RevocationProcessor(
                "CertificateAuthority.revokeAuthority", httpReq.getLocale());
        processor.setSerialNumber(new CertId(authoritySerial));
        processor.setRevocationReason(RevocationReason.UNSPECIFIED);
        processor.setAuthority(this);
        try {
            processor.createCRLExtension();
        } catch (IOException e) {
            throw new ECAException("Unable to create CRL extensions", e);
        }
        processor.addCertificateToRevoke(mCaCert);
        processor.createRevocationRequest();
        processor.auditChangeRequest(ILogger.SUCCESS);
        processor.processRevocationRequest();
        processor.auditChangeRequestProcessed(ILogger.SUCCESS);
    }

    /** Delete keys and certs of this authority from NSSDB.
     */
    void deleteAuthorityNSSDB() throws ECAException {
        if (isHostAuthority()) {
            String msg = "Attempt to delete host authority signing key; not proceeding";
            log(ILogger.LL_WARN, msg);
            logger.debug(msg);
            return;
        }

        CryptoManager cryptoManager;
        try {
            cryptoManager = CryptoManager.getInstance();
        } catch (NotInitializedException e) {
            // can't happen
            throw new ECAException("CryptoManager not initialized");
        }

        // NOTE: PK11Store.deleteCert deletes the cert AND the
        // private key (which is what we want).  A subsequent call
        // to PK11Store.deletePrivateKey() is not necessary and
        // indeed would throw an exception.
        //
        CryptoStore cryptoStore =
            cryptoManager.getInternalKeyStorageToken().getCryptoStore();
        try {
            cryptoStore.deleteCert(mCaX509Cert);
        } catch (NoSuchItemOnTokenException e) {
            logger.warn("deleteAuthority: cert is not on token: " + e);
            // if the cert isn't there, never mind
        } catch (TokenException e) {
            logger.error("deleteAuthority: TokenExcepetion while deleting cert: " + e.getMessage(), e);
            throw new ECAException("TokenException while deleting cert: " + e);
        }
    }

    private void deleteAuthorityEntry(AuthorityID aid) throws ELdapException {
        String dn = "cn=" + aid.toString() + "," + authorityBaseDN();
        LDAPConnection conn = dbFactory.getConn();
        synchronized (hostCA) {
            try {
                conn.delete(dn);
            } catch (LDAPException e) {
                throw new ELdapException("Error deleting authority entry: " + dn, e);
            } finally {
                dbFactory.returnConn(conn);
            }

            String nsUniqueId = nsUniqueIds.get(aid);
            if (nsUniqueId != null)
                deletedNsUniqueIds.add(nsUniqueId);
            forgetAuthority(aid);
        }
    }

    synchronized void readAuthority(LDAPEntry entry) {
        String nsUniqueId =
            entry.getAttribute("nsUniqueId").getStringValueArray()[0];
        if (deletedNsUniqueIds.contains(nsUniqueId)) {
            logger.warn("readAuthority: ignoring entry with nsUniqueId '"
                    + nsUniqueId + "' due to deletion");
            return;
        }

        LDAPAttribute aidAttr = entry.getAttribute("authorityID");
        LDAPAttribute nickAttr = entry.getAttribute("authorityKeyNickname");
        LDAPAttribute keyHostsAttr = entry.getAttribute("authorityKeyHost");
        LDAPAttribute dnAttr = entry.getAttribute("authorityDN");
        LDAPAttribute parentAIDAttr = entry.getAttribute("authorityParentID");
        LDAPAttribute parentDNAttr = entry.getAttribute("authorityParentDN");
        LDAPAttribute serialAttr = entry.getAttribute("authoritySerial");

        if (aidAttr == null || nickAttr == null || dnAttr == null) {
            logger.warn("Malformed authority object; required attribute(s) missing: " + entry.getDN());
            return;
        }

        AuthorityID aid = new AuthorityID(aidAttr.getStringValues().nextElement());

        X500Name dn = null;
        try {
            dn = new X500Name(dnAttr.getStringValues().nextElement());
        } catch (IOException e) {
            logger.warn("Malformed authority object; invalid authorityDN: " + entry.getDN() + ": " + e.getMessage(), e);
        }

        String desc = null;
        LDAPAttribute descAttr = entry.getAttribute("description");
        if (descAttr != null)
            desc = descAttr.getStringValues().nextElement();

        /* Determine if it is the host authority's entry, by
         * comparing DNs.  DNs must be serialised in case different
         * encodings are used for AVA values, e.g. PrintableString
         * from LDAP vs UTF8String in certificate.
         */
        if (dn.toString().equals(mName.toString())) {
            logger.debug("Found host authority");
            foundHostAuthority = true;
            this.authorityID = aid;
            this.authorityDescription = desc;
            caMap.put(aid, this);
            return;
        }

        BigInteger newEntryUSN = null;
        LDAPAttribute entryUSNAttr = entry.getAttribute("entryUSN");
        if (entryUSNAttr == null) {
            logger.debug("readAuthority: no entryUSN");
            if (!entryUSNPluginEnabled()) {
                logger.warn("readAuthority: dirsrv USN plugin is not enabled; skipping entry");
                log(ILogger.LL_FAILURE, "Lightweight authority entry has no"
                        + " entryUSN attribute and USN plugin not enabled;"
                        + " skipping.  Enable dirsrv USN plugin.");
                return;
            } else {
                logger.debug("readAuthority: dirsrv USN plugin is enabled; continuing");
                // entryUSN plugin is enabled, but no entryUSN attribute. We
                // can proceed because future modifications will result in the
                // entryUSN attribute being added.
            }
        } else {
            newEntryUSN = new BigInteger(entryUSNAttr.getStringValueArray()[0]);
            logger.debug("readAuthority: new entryUSN = " + newEntryUSN);
        }

        BigInteger knownEntryUSN = entryUSNs.get(aid);
        if (newEntryUSN != null && knownEntryUSN != null) {
            logger.debug("readAuthority: known entryUSN = " + knownEntryUSN);
            if (newEntryUSN.compareTo(knownEntryUSN) <= 0) {
                logger.debug("readAuthority: data is current");
                return;
            }
        }

        @SuppressWarnings("unused")
        X500Name parentDN = null;
        if (parentDNAttr != null) {
            try {
                parentDN = new X500Name(parentDNAttr.getStringValues().nextElement());
            } catch (IOException e) {
                logger.warn("Malformed authority object; invalid authorityParentDN: " + entry.getDN() + ": " + e.getMessage(), e);
                return;
            }
        }

        String keyNick = nickAttr.getStringValues().nextElement();

        Collection<String> keyHosts;
        if (keyHostsAttr == null) {
            keyHosts = Collections.emptyList();
        } else {
            @SuppressWarnings("unchecked")
            Enumeration<String> keyHostsEnum = keyHostsAttr.getStringValues();
            keyHosts = Collections.list(keyHostsEnum);
        }

        AuthorityID parentAID = null;
        if (parentAIDAttr != null)
            parentAID = new AuthorityID(parentAIDAttr.getStringValues().nextElement());

        BigInteger serial = null;
        if (serialAttr != null)
            serial = new BigInteger(serialAttr.getStringValueArray()[0]);

        boolean enabled = true;
        LDAPAttribute enabledAttr = entry.getAttribute("authorityEnabled");
        if (enabledAttr != null) {
            String enabledString = enabledAttr.getStringValues().nextElement();
            enabled = enabledString.equalsIgnoreCase("TRUE");
        }

        try {
            CertificateAuthority ca = new CertificateAuthority(
                hostCA, dn, aid, parentAID, serial,
                keyNick, keyHosts, desc, enabled);
            caMap.put(aid, ca);
            entryUSNs.put(aid, newEntryUSN);
            nsUniqueIds.put(aid, nsUniqueId);
        } catch (EBaseException e) {
            logger.warn("Error initialising lightweight CA: " + e.getMessage(), e);
        }
    }

    void forgetAuthority(AuthorityID aid) {
        caMap.remove(aid);
        entryUSNs.remove(aid);
        nsUniqueIds.remove(aid);
    }
}
