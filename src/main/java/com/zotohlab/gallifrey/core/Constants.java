/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.gallifrey.core;

/**
 * @author kenl
 */
public class Constants {

  public static String  SYS_DEVID_PFX= "system.####";
  public static String  SYS_DEVID_SFX= "####";

  public static String SYS_DEVID_REGEX= SYS_DEVID_PFX+"[0-9A-Za-z_\\-\\.]+"+SYS_DEVID_SFX;
  public static String SHUTDOWN_DEVID= SYS_DEVID_PFX+"kill_9"+SYS_DEVID_SFX;
  public static String SHUTDOWN_URI="/kill9";

  public static String POD_PROTOCOL = "pod:";
  public static String META_INF = "META-INF";
  public static String POD_INF = "POD-INF";
  public static String WEB_INF = "WEB-INF";
//  public static String PATCH="patch"
//  public static String LIB="lib"
//  public static String CFG="conf"
//  public static String CLASSES="classes"
  public static String DN_BLOCKS = "blocks";
  public static String DN_CORE="exec";
  public static String DN_CONF="conf";
  public static String DN_CLASSES="classes";
  public static String DN_LIB="lib";
  public static String DN_CFG="etc";
  public static String DN_BOXX="apps"    ;
  public static String DN_PODS = "pods";
  public static String DN_LOGS="logs";
  public static String DN_TMP="tmp";
  public static String DN_DBS="dbs";
  public static String DN_DIST="dist";
  public static String DN_TEMPLATES = "templates";
  public static String DN_VIEWS = "views";
  public static String DN_PAGES = "pages";
  public static String DN_PATCH="patch";
  public static String DN_MEDIA="media";
  public static String DN_SCRIPTS="scripts";
  public static String DN_STYLES="styles";
  public static String DN_PUBLIC="public";

  public static String ENV_CF =  "env.conf";
  public static String APP_CF = "app.conf";

  public static String MN_FILE= META_INF + "/" + "MANIFEST.MF";
  public static String POD_CLASSES = POD_INF + "/"+ DN_CLASSES;
  public static String POD_PATCH = POD_INF + "/"+ DN_PATCH;
  public static String POD_LIB = POD_INF + "/"+ DN_LIB;

  public static String WEB_CLASSES = WEB_INF + "/"+ DN_CLASSES;
  public static String WEB_LIB = WEB_INF + "/"+ DN_LIB;
  public static String WEB_LOG = WEB_INF + "/logs";
  public static String WEB_XML = WEB_INF + "/web.xml";

  public static String MN_RNOTES= META_INF + "/" + "RELEASE-NOTES.txt";
  public static String MN_README= META_INF + "/" + "README.md";
  public static String MN_NOTES= META_INF + "/" + "NOTES.txt";
  public static String MN_LIC= META_INF + "/" + "LICENSE.txt";

  public static String CFG_ENV_CF = DN_CONF + "/" + ENV_CF;
  public static String CFG_APP_CF = DN_CONF + "/" + APP_CF;


  public static String PF_SKARO_APPDOMAIN="skaro.app.domain";
  public static String PF_SKARO_APPID="skaro.appid";
  public static String PF_SKARO_APPTASK="skaro.app.task";

  public static String PF_JMXMGM="jmx-management";
  public static String PF_HOMEDIR="skaro.home";
  public static String PF_PROPS="skaro.conf";
  public static String PF_ROUTE_INFO="route.info";
  public static String PF_CLISH="cli-shell";
  public static String PF_COMPS="components";
  public static String PF_REGS="registries";
  public static String PF_KERNEL="kernel";
  public static String PF_EXECV="execvisor";
  public static String PF_DEPLOYER="deployer";
  public static String PF_BLOCKS="blocks";
  public static String PF_APPS="apps";
  public static String PF_SVCS="services";
  public static String PF_LOCALE="locale";
  public static String PF_L10N="l10n";
  public static String PF_PIDFILE="pidfile";

  public static String K_ROOT_CZLR="root.loader";
  public static String K_EXEC_CZLR="exec.loader";

  public static String K_BASEDIR="base.dir";
  public static String K_PODSDIR="pods.dir";
  public static String K_CFGDIR="cfg.dir";
  public static String K_APPDIR="app.dir";
  public static String K_PLAYDIR="play.dir";
  public static String K_LOGDIR="log.dir";
  public static String K_TMPDIR="tmp.dir";
  public static String K_DBSDIR="dbs.dir";
  public static String K_BKSDIR="blocks.dir";

  public static String K_COUNTRY="country";
  public static String K_LOCALE="locale";
  public static String K_LANG="lang";

  public static String K_META="meta";

}

