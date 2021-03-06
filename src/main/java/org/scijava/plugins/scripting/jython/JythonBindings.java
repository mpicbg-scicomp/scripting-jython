/*
 * #%L
 * JSR-223-compliant Jython scripting language plugin.
 * %%
 * Copyright (C) 2008 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.plugins.scripting.jython;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.Bindings;

import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.scijava.script.ScriptModule;

/**
 * A {@link Bindings} wrapper around Jython's local variables.
 *
 * @author Johannes Schindelin
 */
public class JythonBindings implements Bindings {

	protected final PythonInterpreter interpreter;

	/*
	 * NB: In our JythonScriptLanguage we explain the need for cleaning
	 * up after a PythonInterpreter and declare the scope of a
	 * Python environment to be equal to the lifetime of its parent
	 * JythonScriptEngine.
	 * As triggering our cleaning method involves PhantomReferences
	 * we must ensure that JythonScriptEngines can actually be
	 * garbage collected when it is no longer in use.
	 * To do this, we have to prevent JythonScriptEngines from
	 * being passed to the PythonInterpreter - which would then
	 * create a hard reference to the ScriptEngine through the
	 * static PySystemState.
	 * Similarly we do not want to pass ScriptModules to the
	 * PythonInterpreter, as the ScriptModule has a hard
	 * reference to its ScriptEngine.
	 */
	private final Map<String, WeakReference<Object>> shallowMap = new HashMap<>();

	public JythonBindings(final PythonInterpreter interpreter) {
		this.interpreter = interpreter;
	}

	@Override
	public int size() {
		return interpreter.getLocals().__len__();
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public boolean containsKey(final Object key) {
		return get(key) != null;
	}

	@Override
	public boolean containsValue(final Object value) {
		for (final Object value2 : values()) {
			if (value.equals(value2)) return true;
		}
		return false;
	}

	@Override
	public Object get(final Object key) {
		if (shallowMap.containsKey(key)) {
			return shallowMap.get(key).get();
		}

		try {
			return interpreter.get((String) key);
		}
		catch (final Error e) {
			return null;
		}
	}

	@Override
	public Object put(final String key, final Object value) {
		final Object result = get(key);

		if (value instanceof ScriptModule || value instanceof JythonScriptEngine) {
			shallowMap.put(key, new WeakReference<>(value));
		}
		else {
			try {
				interpreter.set(key, value);
			}
			catch (final Error e) {
				// ignore
			}
		}

		return result;
	}

	@Override
	public Object remove(final Object key) {
		final Object result = get(key);
		if (shallowMap.containsKey(key)) shallowMap.remove(key);
		else if (result != null) interpreter.getLocals().__delitem__((String) key);

		return result;
	}

	@Override
	public void putAll(final Map<? extends String, ? extends Object> toMerge) {
		for (final Entry<? extends String, ? extends Object> entry : toMerge
			.entrySet())
		{
			put(entry.getKey(), entry.getValue());
		}
	}

	private PyStringMap dict() {
		return (PyStringMap) interpreter.getLocals();
	}

	@Override
	public void clear() {
		dict().clear();
	}

	@Override
	public Set<String> keySet() {
		final Set<String> result = new HashSet<>();
		for (final Object name : dict().keys()) {
			result.add(name.toString());
		}
		return result;
	}

	@Override
	public Collection<Object> values() {
		final List<Object> result = new ArrayList<>();
		for (final Object name : dict().keys()) {
			try {
				result.add(get(name));
			}
			catch (final Error exc) {
				// ignore for now
			}
		}
		return result;
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		final Set<Entry<String, Object>> result = new HashSet<>();
		for (final Object name : dict().keys()) {
			result.add(new Entry<String, Object>() {

				@Override
				public String getKey() {
					return name.toString();
				}

				@Override
				public Object getValue() {
					return get(name);
				}

				@Override
				public Object setValue(final Object value) {
					throw new UnsupportedOperationException();
				}
			});
		}
		return result;
	}

}
