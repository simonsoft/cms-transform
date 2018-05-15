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
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:cms="http://www.simonsoft.se/namespace/cms">
    
    <xsl:output method="xml" doctype-public="PRIMARY" doctype-system="primary.dtd" indent="yes" />

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" />
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*">
        <xsl:copy>
            <xsl:attribute name="multiple-output">true</xsl:attribute>
            <xsl:apply-templates select="@* | node()" />
        </xsl:copy>
        <!-- <xsl:apply-templates select="//section"/> -->
    </xsl:template>
    
    <xsl:template match="section">
    	<xsl:result-document  href="sections/{@name}">
    		<xsl:copy>
    		    <xsl:attribute name="cms:doctype-public" select="'MULTIPLE'"/>
    		    <xsl:attribute name="cms:doctype-system" select="'multiple.dtd'"/>
				<xsl:apply-templates select="@* | node()" />
			</xsl:copy>
    	</xsl:result-document>
    </xsl:template>

</xsl:stylesheet>