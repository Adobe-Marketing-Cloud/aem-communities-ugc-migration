# communities-ugc-migration
AEM Communities UGC Migration Tool

6/25/2015
This product contains 4 distinct pieces:
- com.adobe.communities.ugc.migration.legacyExport: An exporter package for extracting user-generated content (UGC) from legacy versions of Adobe Experience Manager versions 5.6.1 and 6.0. Additionally, this package can export profile scores.
- com.adobe.communities.ugc.migration.legacyProfileExport: An exporter package specifically for exporting profile data (messages and social-graph) from AEM 6.0, where those features were introduced.
- package com.adobe.communities.ugc.migration: Eventually, this will provide both an exporter and an importer service for UGC and profile data into and out of AEM 6.1. However, as of this writing, only the importer has been constructed.
- communities-ugc-migration-pkg: This package provides a graphical user interface for importing UGC into 6.1. It must be installed in /crx/packMgr. The UI shows up in the admin section at "libs/social/console/content/importUpload.html".

# Exporting UGC using the legacy export servlet
- Build the package using maven. 
- Install the resulting .jar file in /system/console/bundles of the machine you want to export from.
- Go to /crx/de and expand /content/usergenerated to find the root node for the content you wish to export. Copy the 
