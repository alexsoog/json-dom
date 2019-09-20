package de.larssh.json.dom;

import static de.larssh.utils.Collectors.toLinkedHashMap;
import static de.larssh.utils.Finals.constant;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;

import de.larssh.json.dom.values.JsonDomValue;
import de.larssh.utils.Finals;
import de.larssh.utils.Nullables;
import de.larssh.utils.text.Patterns;
import de.larssh.utils.text.Strings;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * JSON DOM implementation of {@link Element}.
 *
 * @param <T> implementation specific JSON element type
 */
@Getter
@EqualsAndHashCode(callSuper = true, onParam_ = { @Nullable })
public class JsonDomElement<T> extends JsonDomNode<T> implements Element {
	/**
	 * Name of the attribute {@code name}
	 */
	public static final String ATTRIBUTE_NAME = constant("name");

	/**
	 * Name of the attribute {@code type}
	 */
	public static final String ATTRIBUTE_TYPE = constant("type");

	/**
	 * The special value "*" matches all tags.
	 */
	private static final String GET_ELEMENTS_BY_TAG_NAME_WILDCARD = "*";

	/**
	 * Regex character class expression describing the first character of an XML
	 * name following
	 * <a href="https://www.w3.org/TR/2006/REC-xml11-20060816/#sec-common-syn">the
	 * XML 1.1 standard</a>.
	 */
	@SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
	private static final String XML_NAME_START_CHARACTERS
			= ":A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD";

	/**
	 * Regex character class expression describing the second and following
	 * characters of an XML name following
	 * <a href="https://www.w3.org/TR/2006/REC-xml11-20060816/#sec-common-syn">the
	 * XML 1.1 standard</a>.
	 */
	@SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
	private static final String XML_NAME_CHARACTERS
			= XML_NAME_START_CHARACTERS + "-.0-9\u00B7\u0300-\u036F\u203F-\u2040";

	/**
	 * Pattern finding one or more illegal XML characters
	 */
	@SuppressWarnings("checkstyle:MultipleStringLiterals")
	private static final Pattern PATTERN_ILLEGAL_XML_CHARACTERS = Pattern.compile("[^" + XML_NAME_CHARACTERS + "]+");

	/**
	 * Pattern finding one or more illegal XML characters at the start
	 */
	@SuppressWarnings("checkstyle:MultipleStringLiterals")
	private static final Pattern PATTERN_ILLEGAL_XML_CHARACTERS_AT_START
			= Pattern.compile("^[^" + XML_NAME_START_CHARACTERS + "]+");

	/**
	 * Pattern matching any number.
	 */
	private static final Pattern PATTERN_IS_NUMBER = Pattern.compile("^(0|[^1-9]\\d*)$");

	/**
	 * Returns an XML compatible tag name based on the original JSON key.
	 *
	 * <p>
	 * The node names inside a JSON DOM are compatible with the XML standard.
	 * Therefore keys that are invalid XML tag names are replaced inside JSON DOM.
	 * The attribute {@code name} still contains the original JSON object key.
	 *
	 * @param jsonKey the elements JSON key
	 * @return the XML compatible tag name
	 */
	private static String createTagName(final String jsonKey) {
		// Empty
		if (jsonKey.isEmpty()) {
			return "empty";
		}

		// Spaces
		final String trimmedName = jsonKey.trim();
		if (trimmedName.isEmpty()) {
			return jsonKey.length() == 1 ? "whitespace" : "whitespaces";
		}

		// Numeric
		if (Patterns.matches(PATTERN_IS_NUMBER, trimmedName).isPresent()) {
			return 'n' + trimmedName;
		}

		// Simplified
		final String simplifiedName = Strings.replaceFirst(trimmedName, PATTERN_ILLEGAL_XML_CHARACTERS_AT_START, "");
		if (!simplifiedName.isEmpty()) {
			return Strings.replaceAll(simplifiedName, PATTERN_ILLEGAL_XML_CHARACTERS, "-");
		}

		// Non-Empty
		return "non-empty";
	}

	/**
	 * Map of attribute names to attribute node
	 *
	 * @return map of attribute names to attribute node
	 */
	Supplier<JsonDomNamedNodeMap<JsonDomAttribute<T>>> attributes = Finals
			.lazy(() -> new JsonDomNamedNodeMap<>(asList(new JsonDomAttribute<>(this, ATTRIBUTE_NAME, this::getJsonKey),
					new JsonDomAttribute<>(this, ATTRIBUTE_TYPE, () -> getJsonDomValue().getType().getValue())).stream()
							.collect(toLinkedHashMap(JsonDomAttribute::getNodeName, Function.identity()))));

	/**
	 * List of child element nodes
	 */
	Supplier<JsonDomNodeList<JsonDomNode<T>>> childNodes
			= Finals.lazy(() -> new JsonDomNodeList<>(getJsonDomValue().getType().isComplex()
					? getJsonDomValue().getChildren()
							.entrySet()
							.stream()
							.map(entry -> new JsonDomElement<>(this, entry.getKey(), entry.getValue()))
							.collect(toList())
					: singletonList(new JsonDomText<>(this, getJsonDomValue().getTextValue()))));

	/**
	 * Wrapped JSON element
	 *
	 * @return wrapped JSON element
	 */
	JsonDomValue<T> jsonDomValue;

	/**
	 * The elements JSON key
	 *
	 * @return the elements JSON key
	 */
	String jsonKey;

	/**
	 * Constructor of {@link JsonDomElement}.
	 *
	 * @param parentNode   parent node
	 * @param jsonKey      the elements JSON key
	 * @param jsonDomValue wrapped JSON element
	 */
	public JsonDomElement(final JsonDomNode<T> parentNode, final String jsonKey, final JsonDomValue<T> jsonDomValue) {
		super(parentNode, createTagName(jsonKey));

		this.jsonDomValue = jsonDomValue;
		this.jsonKey = jsonKey;
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public String getAttribute(@Nullable final String name) {
		final JsonDomAttribute<T> attribute = getAttributeNode(name);
		return attribute == null ? "" : attribute.getValue();
	}

	/** {@inheritDoc} */
	@Nullable
	@Override
	public JsonDomAttribute<T> getAttributeNode(@Nullable final String name) {
		return Nullables.orElseThrow(getAttributes()).get(name);
	}

	/** {@inheritDoc} */
	@Nullable
	@Override
	public JsonDomAttribute<T> getAttributeNodeNS(@Nullable @SuppressWarnings("unused") final String namespaceURI,
			@Nullable @SuppressWarnings("unused") final String localName) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public String getAttributeNS(@Nullable @SuppressWarnings("unused") final String namespaceURI,
			@Nullable @SuppressWarnings("unused") final String localName) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public JsonDomNamedNodeMap<JsonDomAttribute<T>> getAttributes() {
		return attributes.get();
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public JsonDomNodeList<JsonDomNode<T>> getChildNodes() {
		return childNodes.get();
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public JsonDomNodeList<JsonDomElement<T>> getElementsByTagName(@Nullable final String name) {
		if (name == null) {
			return new JsonDomNodeList<>(emptyList());
		}

		final List<JsonDomElement<T>> list = new ArrayList<>();
		for (final JsonDomNode<T> child : getChildNodes()) {
			if (child instanceof JsonDomElement) {
				final JsonDomElement<T> childElement = (JsonDomElement<T>) child;
				if (name.equals(childElement.getTagName()) || GET_ELEMENTS_BY_TAG_NAME_WILDCARD.equals(name)) {
					list.add(childElement);
				}
				list.addAll(childElement.getElementsByTagName(name));
			}
		}
		return new JsonDomNodeList<>(list);
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public JsonDomNodeList<JsonDomElement<T>> getElementsByTagNameNS(
			@Nullable @SuppressWarnings("unused") final String namespaceURI,
			@Nullable @SuppressWarnings("unused") final String localName) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public T getJsonElement() {
		return getJsonDomValue().getJsonElement();
	}

	/** {@inheritDoc} */
	@Nullable
	@Override
	public JsonDomElement<T> getNextSibling() {
		final JsonDomNode<T> parentNode = Nullables.orElseThrow(getParentNode());
		final JsonDomNodeList<JsonDomNode<T>> children = parentNode.getChildNodes();
		final int index = children.indexOf(this);
		return index + 1 < children.size() ? (JsonDomElement<T>) children.get(index + 1) : null;
	}

	/** {@inheritDoc} */
	@Override
	public short getNodeType() {
		return Node.ELEMENT_NODE;
	}

	/** {@inheritDoc} */
	@Nullable
	@Override
	public String getNodeValue() {
		return null;
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public JsonDomDocument<T> getOwnerDocument() {
		return Nullables.orElseThrow(getParentNode()).getOwnerDocument();
	}

	/** {@inheritDoc} */
	@Nullable
	@Override
	public JsonDomElement<T> getPreviousSibling() {
		final JsonDomNode<T> parentNode = Nullables.orElseThrow(getParentNode());
		final JsonDomNodeList<JsonDomNode<T>> children = parentNode.getChildNodes();
		final int index = children.indexOf(this);
		return index > 0 ? (JsonDomElement<T>) children.get(index - 1) : null;
	}

	/** {@inheritDoc} */
	@Nullable
	@Override
	public TypeInfo getSchemaTypeInfo() {
		return null;
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public String getTagName() {
		return getNodeName();
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public String getTextContent() {
		return "";
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasAttribute(@Nullable final String name) {
		return Nullables.orElseThrow(getAttributes()).containsKey(name);
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasAttributeNS(@Nullable @SuppressWarnings("unused") final String namespaceURI,
			@Nullable @SuppressWarnings("unused") final String localName) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public void removeAttribute(@Nullable @SuppressWarnings("unused") final String name) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public JsonDomAttribute<T> removeAttributeNode(@Nullable @SuppressWarnings("unused") final Attr oldAttr) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public void removeAttributeNS(@Nullable @SuppressWarnings("unused") final String namespaceURI,
			@Nullable @SuppressWarnings("unused") final String localName) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public void setAttribute(@Nullable @SuppressWarnings("unused") final String name,
			@Nullable @SuppressWarnings("unused") final String value) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public JsonDomAttribute<T> setAttributeNode(@Nullable @SuppressWarnings("unused") final Attr newAttr) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Nullable
	@Override
	public JsonDomAttribute<T> setAttributeNodeNS(@Nullable @SuppressWarnings("unused") final Attr newAttr) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public void setAttributeNS(@Nullable @SuppressWarnings("unused") final String namespaceURI,
			@Nullable @SuppressWarnings("unused") final String qualifiedName,
			@Nullable @SuppressWarnings("unused") final String value) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public void setIdAttribute(@Nullable @SuppressWarnings("unused") final String name,
			@SuppressWarnings("unused") final boolean isId) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public void setIdAttributeNode(@Nullable @SuppressWarnings("unused") final Attr idAttr,
			@SuppressWarnings("unused") final boolean isId) {
		throw new JsonDomNotSupportedException();
	}

	/** {@inheritDoc} */
	@Override
	public void setIdAttributeNS(@Nullable @SuppressWarnings("unused") final String namespaceURI,
			@Nullable @SuppressWarnings("unused") final String localName,
			@SuppressWarnings("unused") final boolean isId) {
		throw new JsonDomNotSupportedException();
	}
}
