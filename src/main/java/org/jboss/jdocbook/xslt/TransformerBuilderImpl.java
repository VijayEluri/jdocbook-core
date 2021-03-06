/*
 * jDocBook, processing of DocBook sources
 *
 * Copyright (c) 2010, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.jboss.jdocbook.xslt;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamSource;

import com.icl.saxon.Controller;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.jboss.jdocbook.Configuration;
import org.jboss.jdocbook.Environment;
import org.jboss.jdocbook.JDocBookComponentRegistry;
import org.jboss.jdocbook.ResourceDelegate;
import org.jboss.jdocbook.util.NoOpWriter;

/**
 * Implementation of the {@link TransformerBuilder} contract
 *
 * @author Steve Ebersole
 */
public class TransformerBuilderImpl implements TransformerBuilder {
	private final JDocBookComponentRegistry componentRegistry;
	private final CatalogResolver catalogResolver;

	public TransformerBuilderImpl(JDocBookComponentRegistry componentRegistry) {
		this.componentRegistry = componentRegistry;

		final CatalogManager catalogManager;
		if ( componentRegistry.getConfiguration().getCatalogs() == null
				|| componentRegistry.getConfiguration().getCatalogs().size() == 0 ) {
			catalogManager = new ImplicitCatalogManager();
		}
		else {
			catalogManager = new ExplicitCatalogManager( componentRegistry.getConfiguration().getCatalogs() );
		}
		catalogResolver = new CatalogResolver( catalogManager );
	}

	@Override
	public CatalogResolver getCatalogResolver() {
		return catalogResolver;
	}

	protected Environment environment() {
		return componentRegistry.getEnvironment();
	}

	protected ResourceDelegate resourceDelegate() {
		return environment().getResourceDelegate();
	}

	protected Configuration configuration() {
		return componentRegistry.getConfiguration();
	}

	@Override
	public Transformer buildStandardTransformer(URL xslt) {
		URIResolver uriResolver = buildStandardUriResolver();
		return buildTransformer( xslt, uriResolver );
	}

	@Override
	public Transformer buildStandardTransformer(String xsltResource) {
		URIResolver uriResolver = buildStandardUriResolver();
		return buildTransformer( resourceDelegate().requireResource( xsltResource ), uriResolver );
	}

	private ResolverChain buildStandardUriResolver() {
		ResolverChain resolverChain = new ResolverChain();
		applyStandardResolvers( resolverChain );
		return resolverChain;
	}

	private void applyStandardResolvers(ResolverChain resolverChain) {
		// See https://jira.jboss.org/jira/browse/MPJDOCBOOK-49
		resolverChain.addResolver( new VersionResolver( componentRegistry ) );
		resolverChain.addResolver( new RelativeJarUriResolver() );
		resolverChain.addResolver( new ClasspathResolver( componentRegistry ) );
		resolverChain.addResolver( catalogResolver );
	}

	@Override
	public Transformer buildTransformer(FormatPlan formatPlan, URL customStylesheet) throws XSLTException {
		URIResolver uriResolver = buildStandardUriResolver();
		URL xsltStylesheet = customStylesheet == null
				? resourceDelegate().requireResource( formatPlan.getStylesheetResource() )
				: customStylesheet;
		return buildTransformer( xsltStylesheet, uriResolver );
	}

	private HashMap<String, Templates> transformerTemplatesCache = new HashMap<String, Templates>();

	protected Transformer buildTransformer(URL xslt, URIResolver uriResolver) throws XSLTException {
		javax.xml.transform.TransformerFactory transformerFactory = buildSAXTransformerFactory();
		transformerFactory.setURIResolver( uriResolver );

		final String xsltUrlStr = xslt.toExternalForm();

		Transformer transformer;
		try {
			Templates transformerTemplates = transformerTemplatesCache.get( xsltUrlStr );
			if ( transformerTemplates == null ) {
				Source source = new StreamSource( xslt.openStream(), xsltUrlStr );
				transformerTemplates = transformerFactory.newTemplates( source );
				transformerTemplatesCache.put( xsltUrlStr, transformerTemplates );
			}
			transformer = transformerTemplates.newTransformer();
			//Source source = new StreamSource( xslt.openStream(), xsltUrlStr );
			//transformer = transformerFactory.newTransformer( source );
		}
		catch ( IOException e ) {
			throw new XSLTException( "problem opening stylesheet [" + xsltUrlStr + "]", e );
		}
		catch ( TransformerConfigurationException e ) {
			throw new XSLTException( "unable to build transformer [" + e.getLocationAsString() + "] : " + e.getMessage(), e );
		}

		configureTransformer( transformer, uriResolver, configuration().getTransformerParameters() );
		return transformer;
	}

	private SAXTransformerFactory buildSAXTransformerFactory() {
		return new com.icl.saxon.TransformerFactoryImpl();
	}

//	private SAXTransformerFactory buildSAXTransformerFactory() {
//		com.icl.saxon.TransformerFactoryImpl factoryImpl = new com.icl.saxon.TransformerFactoryImpl();
//		factoryImpl.setAttribute( "http://icl.com/saxon/feature/messageEmitterClass", SaxonXslMessageEmitter.class.getName() );
//		return factoryImpl;
//	}
//
//	private static class SaxonXslMessageEmitter extends com.icl.saxon.output.Emitter {
//		private static final Logger log = LoggerFactory.getLogger( SaxonXslMessageEmitter.class );
//
//		private StringBuffer buffer;
//
//		@Override
//		public void startDocument() throws TransformerException {
//			if ( buffer != null ) {
//				System.out.println( "Unexpected call sequence on SaxonXslMessageEmitter; discarding [" + buffer.toString() + "]" );
//			}
//			buffer = new StringBuffer();
//		}
//
//		@Override
//		public void endDocument() throws TransformerException {
//			log.trace( "[STYLESHEET MESSAGE] " + buffer.toString() );
//			buffer.setLength( 0 );
//			buffer = null;
//		}
//
//		@Override
//		public void startElement(int i, Attributes attributes, int[] ints, int i1) throws TransformerException {
//		}
//
//		@Override
//		public void endElement(int i) throws TransformerException {
//		}
//
//		@Override
//		public void characters(char[] chars, int start, int end) throws TransformerException {
//			for ( int i = start; i < end; i++ ) {
//				buffer.append( chars[i] );
//			}
//		}
//
//		@Override
//		public void processingInstruction(String s, String s1) throws TransformerException {
//		}
//
//		@Override
//		public void comment(char[] chars, int i, int i1) throws TransformerException {
//		}
//	}

	private static void configureTransformer(Transformer transformer, URIResolver uriResolver, Map<String,String> transformerParameters) {
		if ( transformer instanceof Controller ) {
			Controller controller = ( Controller ) transformer;
			try {
				controller.makeMessageEmitter();
				controller.getMessageEmitter().setWriter( new NoOpWriter() );
			}
			catch ( TransformerException te ) {
				// intentionally empty
			}
		}

		transformer.setURIResolver( uriResolver );
		transformer.setParameter( "fop.extensions", "0" );
		transformer.setParameter( "fop1.extensions", "1" );

		if ( transformerParameters == null ) {
			return;
		}
		for ( Map.Entry<String, String> entry : transformerParameters.entrySet() ) {
			transformer.setParameter( entry.getKey(), entry.getValue() );
		}
	}

}
