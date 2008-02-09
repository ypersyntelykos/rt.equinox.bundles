/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.equinox.transforms;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Class that represents a dynamic list of TransformTuples that have been
 * registered against a particular transform type.
 */
public class TransformInstanceListData extends ServiceTracker {
	/**
	 * Stale state of the transform list. Set to true whenever one of the
	 * ServiceTrackerCustomization methods are invoked.
	 */
	private volatile boolean stale = true;

	/**
	 * Map from transformer class -> tuple array
	 */
	private Map transformerToTuple = new HashMap();

	/**
	 * List of all tuples in the system.
	 */
	private List rawTuples = new ArrayList();

	/**
	 * Map from bundle ID -> boolean representing whether or not a given bundle
	 * currently has any transforms registered against it.
	 */
	private Map bundleIdToTransformPresence = new HashMap();

	/**
	 * Create a new transform list bound to the given context. If new transforms
	 * are registered against the given context the contents of this list will
	 * change.
	 * 
	 * @param context
	 *            the bundle context
	 * 
	 * @throws InvalidSyntaxException
	 *             thrown if there's an issue listening for changes to the given
	 *             transformer type
	 */
	public TransformInstanceListData(BundleContext context)
			throws InvalidSyntaxException {
		super(context, context.createFilter("(&(objectClass="
				+ URL.class.getName() + ")(" + TransformTuple.TRANSFORMER_TYPE
				+ "=*))"), null);
		open();
	}

	/**
	 * Return the transforms currently held by this list. If a change has been
	 * detected since the last request this list will be rebuilt.
	 * 
	 * @return the transforms currently held by this list
	 */
	public synchronized TransformTuple[] getTransformsFor(
			String transformerClass) {
		if (stale)
			rebuildTransformMap();

		return (TransformTuple[]) transformerToTuple.get(transformerClass);
	}

	/**
	 * Return whether or not there are any transforms who's bundle pattern
	 * matches the ID of the provided bundle. Only transforms with a present
	 * transform handler are considered during the invocation of this method.
	 * 
	 * @param bundle
	 *            the bundle to test
	 * @return the presence of associated transforms.
	 */
	public synchronized boolean hasTransformsFor(Bundle bundle) {
		if (stale)
			rebuildTransformMap();

		String bundleName = bundle.getSymbolicName();
		Boolean hasTransformsFor = (Boolean) bundleIdToTransformPresence
				.get(bundleName);

		if (hasTransformsFor == null) {
			hasTransformsFor = Boolean.FALSE;
			for (Iterator i = rawTuples.iterator(); i.hasNext();) {
				TransformTuple tuple = (TransformTuple) i.next();
				if (tuple.bundlePattern.matcher(bundleName).matches()) {
					hasTransformsFor = Boolean.TRUE;
				}
			}

			bundleIdToTransformPresence.put(bundleName, hasTransformsFor);
		}

		return hasTransformsFor.booleanValue();
	}

	private void rebuildTransformMap() {
		transformerToTuple.clear();
		rawTuples.clear();
		bundleIdToTransformPresence.clear();

		ServiceReference[] serviceReferences = getServiceReferences();
		stale = false;
		if (serviceReferences == null)
			return;

		for (int i = 0; i < serviceReferences.length; i++) {
			ServiceReference serviceReference = serviceReferences[i];
			String type = serviceReference.getProperty(
					TransformTuple.TRANSFORMER_TYPE).toString();

			URL url = (URL) getService(serviceReference);
			TransformTuple[] transforms;
			try {
				transforms = CSVParser.parse(context, url);
				TransformTuple[] existing = (TransformTuple[]) transformerToTuple
						.get(type);
				if (existing != null) {
					TransformTuple[] newTransforms = new TransformTuple[existing.length
							+ transforms.length];
					System.arraycopy(existing, 0, newTransforms, 0,
							existing.length);
					System.arraycopy(transforms, 0, newTransforms,
							existing.length, transforms.length);
					transformerToTuple.put(type, newTransforms);
				} else
					transformerToTuple.put(type, transforms);

				for (int j = 0; j < transforms.length; j++) {
					rawTuples.add(transforms[i]);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.ServiceReference)
	 */
	public Object addingService(ServiceReference reference) {
		try {
			return super.addingService(reference);
		} finally {
			stale = true;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.util.tracker.ServiceTracker#modifiedService(org.osgi.framework.ServiceReference,
	 *      java.lang.Object)
	 */
	public void modifiedService(ServiceReference reference, Object service) {
		super.modifiedService(reference, service);
		stale = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.ServiceReference,
	 *      java.lang.Object)
	 */
	public void removedService(ServiceReference reference, Object service) {
		super.removedService(reference, service);
		stale = true;
	}

}