<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright (C) 2009-2017 Simonsoft Nordic AB

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

            http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- CMS-2028 A text result-document (e.g. a script), the principal output is discarded (output param set to empty string). -->
    <xsl:template match="/">
        <xsl:result-document href="/doc/scripts/transform-test.txt" method="text">
            <xsl:text># Script with one line per section&#10;</xsl:text>
            <xsl:for-each select="//section">
                <xsl:value-of select="'svn propset meta:sample ''' || @name || ''' &lt;path&gt; &amp;&#10;'"/>
            </xsl:for-each>
        </xsl:result-document>
        <!-- Without root element: discarded. -->
        <xsl:result-document href="/doc/scripts/no-root.xml">
            <xsl:comment>no root element</xsl:comment>
        </xsl:result-document>
    </xsl:template>

</xsl:stylesheet>
