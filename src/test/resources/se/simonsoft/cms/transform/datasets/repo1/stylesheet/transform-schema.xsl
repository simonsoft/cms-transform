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
<!-- Produces a topic (principal output) and a map (result document), optionally with schema issues. -->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:cms="http://www.simonsoft.se/namespace/cms">
    <xsl:output method="xml" indent="yes" />

    <xsl:param name="invalid" select="'false'"/>
    <xsl:param name="doctype" select="'false'"/>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" />
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/*">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" />
        </xsl:copy>
        <xsl:result-document href="transform-test-map.ditamap">
            <techdocmap>
                <xsl:if test="$doctype = 'true'">
                    <!-- DOCTYPE with a DTD that does not exist (see output.xsl). -->
                    <xsl:attribute name="cms:doctype-public" select="'-//Simonsoft//DTD Missing//EN'"/>
                    <xsl:attribute name="cms:doctype-system" select="'missing.dtd'"/>
                </xsl:if>
                <title><xsl:value-of select="title"/></title>
                <xsl:for-each select="body/section">
                    <topicref href="{@name}"/>
                </xsl:for-each>
                <xsl:if test="$invalid = 'true'">
                    <reltable/>
                </xsl:if>
            </techdocmap>
        </xsl:result-document>
    </xsl:template>

    <xsl:template match="p[$invalid = 'true']">
        <para>
            <xsl:apply-templates select="@* | node()" />
        </para>
    </xsl:template>

</xsl:stylesheet>
