# communities-ugc-migration
AEM Communities UGC Migration Tool

6/25/2015
This product contains 4 distinct pieces:
- com.adobe.communities.ugc.migration.legacyExport: An exporter package for extracting user-generated content (UGC) from legacy versions of Adobe Experience Manager versions 5.6.1 and 6.0. Additionally, this package can export profile scores.
- com.adobe.communities.ugc.migration.legacyProfileExport: An exporter package specifically for exporting profile data (messages and social-graph) from AEM 6.0, where those features were introduced.
- package com.adobe.communities.ugc.migration: Eventually, this will provide both an exporter and an importer service for UGC and profile data into and out of AEM 6.1. However, as of this writing, only the importer has been constructed.
- communities-ugc-migration-pkg: This package provides a graphical user interface for importing UGC into 6.1. It must be installed in /crx/packMgr. The UI shows up in the admin section at "libs/social/console/content/importUpload.html".

# Supported types for UGC Migration
The following types of UGC can be migrated from 5.6.1 to 6.1:
1. Forums
2. Question and Answers
3. Comments
4. Journals
5. Calendars
6. Ratings
7. Scoring

Additionally, the following types can be migrated from 6.0 to 6.1
1. Messages
2. Social Graphs (who follows who)

Since messages and social graphs can only be migrated from 6.0 (as they didn't exist in 5.6.1) there is a separate bundle that handles their export

# Exporting UGC using the legacy export servlet (works in both 5.6.1 and 6.0 installations)
1. Build the package "communities-ugc-migration-legacyExport" using maven.
2. Install the resulting .jar file in /system/console/bundles of the machine you want to export from.
3. Go to /crx/de and expand /content/usergenerated to find the root node for the content you wish to export. Copy the path to the root of the components you wish to export.
4. In your browser, go to /services/social/ugc/export?path=[path you copied in crx/de]. This will trigger a file download of a zip archive containing all the UGC at or below the root node path you provided.
5. To export profile scores, you will need to look up the path to your system's profiles. By default, it is /home/users, but your value may be custom.
6. Go to /services/social/scores/export?path=/home/users (replace /home/users with your own value if it's different). Save the response as a json file.

# Exporting Messages and Social Graphs from a 6.0 system
1. Build the package "communities-ugc-migration-legacyProfileExport" using maven.
2. Install the resulting .jar file in /system/console/bundles of the machine you want to export from.
3. To export messages in a zip archive, navigate to /services/social/messages/export.
4. To export social graph, you will need to look up the path to your system's profiles. By default, it is /home/users, but your value may be custom.
6. Go to /services/social/graph/export?path=/home/users (replace /home/users with your own value if it's different). Save the response as a json file.

# Importing UGC into 6.1 using the import servlet with UI
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. Build the package "communities-ugc-migration-pkg" using maven.
4. Install the .zip package file in /crx/packMgr.
5. If you haven't already, you'll need to create the UGC component nodes for the content you want to migrate.
6. In your browser, visit /libs/social/console/content/importUpload.html
7. Use the form on that page to upload the zip archive you downloaded during the export process.
8. The form on the page should expand to show a dropdown with a list of files extracted from the archive just uploaded, along with a text input field. The files from the archive are named according to their relative path below the root node you selected for export. Use that relative path to determine where in the new system you want that content to reside. For each file to be imported, select it from the dropdown, then put in the path to the UGC component node that matches the type of content you want to import and press the import button.

As the content is imported, the file containing that content will automatically be deleted from the system. If you want to delete, rather than import, any of the files extracted from the archive, just select them in the dropdown list and press the "delete" button.

# Importing Profile Score data into 6.1
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. Use curl to upload the scores file to the import servlet
4. Example import command:
curl -i -u"admin:admin" -X POST -F"file=@/Users/masonw/Downloads/socialScores.json" http://localhost:4503/services/social/scores/import

# Importing Messages data into 6.1
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. Use curl to upload the social graph file to the import servlet
4. Example import command:
curl -i -u"admin:admin" -X POST -F"file=@/Users/masonw/Downloads/socialGraph.json" http://localhost:4503/services/social/graph/import
