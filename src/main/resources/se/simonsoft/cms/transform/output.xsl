<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:fn="http://www.w3.org/2005/xpath-functions"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:xi="http://www.w3.org/2001/XInclude"
	xmlns:cms="http://www.simonsoft.se/namespace/cms"
	xmlns:cmsfn="http://www.simonsoft.se/namespace/cms-functions"
	version="2.0">
	
	<!-- Caller can request omitting the declaration, e.g. when storing in a property. -->
	<xsl:param name="omit-xml-declaration" select="'no'"/>
	
	<xsl:template match="@*|node()" mode="#all">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" mode="#current"/>
		</xsl:copy>
	</xsl:template>
	
	
	<xsl:template match="/">
		<!-- For completeness: If this template was called from the document node, ensure that nothing is output before result-document. -->
		<!-- The Prepare Release service performs this transform with the document root element as initial context. -->
		<xsl:apply-templates select="element()"/>
	</xsl:template>
	
	<!-- Match the document root element. -->
	<xsl:template match="/element()"  priority="10">
		<xsl:variable name="doctype.public" as="xs:string?" select="/*/@cms:doctype-public"/>
		<xsl:variable name="doctype.system" as="xs:string?">
			<xsl:if test="boolean($doctype.public)">
				<!-- DOCTYPE declaration will be set ONLY is both public and system IDs are present. -->
				<!-- Schema with xsi delaration will have empty PublicId and the schema location in SystemId (will propagate as an attribute) -->
				<xsl:value-of select="/*/@cms:doctype-system"/>
			</xsl:if>
		</xsl:variable>
		
		<xsl:message select="concat('output.xsl: ',$doctype.public)"></xsl:message>
		<!-- Using 'xml' output method will preserve minimized empty tags from input. Actually, 'xml' will minimize all tags that happen to be empty. -->
		<xsl:result-document method="xml" doctype-public="{$doctype.public}" doctype-system="{$doctype.system}" omit-xml-declaration="{$omit-xml-declaration}">
			<!-- A DOCTYPE declaration will be output by Saxon if a doctype-system is present. -->
			<xsl:apply-templates select="." mode="output"/>
		</xsl:result-document>
	</xsl:template>
	
	<xsl:template match="@cms:doctype-public | @cms:doctype-system" mode="output">
		<!-- Suppressing temporary attributes from URI-resolver. -->
		<!-- This is a standard template identical in many services. Overriding below for attributes explicitly preserved. -->
	</xsl:template>
	
</xsl:stylesheet>
