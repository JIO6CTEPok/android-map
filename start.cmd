@Echo off
REM This file will start the TrekBuddy Atlas Creator with custom memory settings for
REM the JVM. With the below settings the heap size (Available memory for the application)
REM will range from 64 megabyte up to 512 megabyte.

REM start javaw.exe -jar TrekBuddy_Atlas_Creator.jar -Xms64M -Xmx512M
start javaw.exe -Djava.library.path=lib -jar TrekBuddy_Atlas_Creator.jar -Xms64M -Xmx512M
